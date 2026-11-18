package io.fulfillx.inventory;

import io.fulfillx.inventory.api.dto.SetStockRequest;
import io.fulfillx.inventory.application.InventoryApplicationService;
import io.fulfillx.inventory.application.InventoryCommandService;
import io.fulfillx.inventory.messaging.CommandEnvelope;
import io.fulfillx.inventory.messaging.InventoryMessagingProperties;
import io.fulfillx.inventory.messaging.InventoryOutboxProperties;
import io.fulfillx.inventory.messaging.InventoryOutboxPublisher;
import io.fulfillx.inventory.messaging.ReserveInventoryPayload;
import io.fulfillx.inventory.persistence.OutboxStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class InventoryReservationIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("inventory.messaging.consumer-enabled", () -> "false");
        registry.add("inventory.outbox.publisher-enabled", () -> "false");
    }

    @Autowired InventoryApplicationService inventory;
    @Autowired InventoryCommandService commands;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxStore outbox;
    @Autowired MockMvc mvc;

    @BeforeEach
    void clearDatabase() {
        removeRollbackTrigger();
        jdbc.update("DELETE FROM inventory_outbox");
        jdbc.update("DELETE FROM processed_messages");
        jdbc.update("DELETE FROM reservation_lines");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM inventory_items");
    }

    @Test
    void inventoryApiRequiresOperationsRoleAndActuatorRequiresAdmin() throws Exception {
        UUID productId = UUID.randomUUID();
        inventory.setStock(productId, new SetStockRequest(5L));
        mvc.perform(get("/api/v1/inventory/" + productId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inventory/" + productId).header("Authorization", "Bearer malformed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/inventory/" + productId).with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/inventory/" + productId).with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS"))))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/prometheus").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory")));
    }

    @AfterEach
    void removeRollbackTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_inventory_reserved_outbox_trigger ON inventory_outbox");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_inventory_reserved_outbox()");
    }

    @Test
    void reservesAllLinesAndPersistsOneResult() {
        UUID mug = UUID.randomUUID();
        UUID tote = UUID.randomUUID();
        stock(mug, 5);
        stock(tote, 3);

        UUID orderId = UUID.randomUUID();
        commands.reserve(envelope("ReserveInventory", orderId), reserve(orderId, line(mug, 2), line(tote, 2)));

        assertEquals(3, inventory.get(mug).availableQuantity());
        assertEquals(1, inventory.get(tote).availableQuantity());
        assertEquals("RESERVED", reservationStatus(orderId));
        assertEquals(1, outboxCount("InventoryReserved"));
    }

    @Test
    void insufficientLineRejectsWholeOrderWithoutPartialReservation() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stock(first, 6);
        stock(second, 1);
        UUID orderId = UUID.randomUUID();

        commands.reserve(envelope("ReserveInventory", orderId), reserve(orderId, line(first, 4), line(second, 2)));

        assertEquals(6, inventory.get(first).availableQuantity());
        assertEquals(1, inventory.get(second).availableQuantity());
        assertEquals("REJECTED", reservationStatus(orderId));
        assertEquals(1, outboxCount("InventoryRejected"));
        assertEquals(0, outboxCount("InventoryReserved"));
    }

    @Test
    void redeliveryOfSameCommandDoesNotReserveOrPublishTwice() {
        UUID product = UUID.randomUUID();
        stock(product, 5);
        UUID orderId = UUID.randomUUID();
        CommandEnvelope message = envelope("ReserveInventory", orderId);
        ReserveInventoryPayload payload = reserve(orderId, line(product, 2));

        commands.reserve(message, payload);
        commands.reserve(message, payload);

        assertEquals(3, inventory.get(product).availableQuantity());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE order_id = ?", Integer.class, orderId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM processed_messages WHERE message_id = ?", Integer.class, message.messageId()));
        assertEquals(1, outboxCount("InventoryReserved"));
    }

    @Test
    void competingOrdersCannotReserveMoreThanTheLastUnits() throws Exception {
        UUID product = UUID.randomUUID();
        stock(product, 5);
        UUID firstOrder = UUID.randomUUID();
        UUID secondOrder = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> concurrentReserve(ready, start, firstOrder, product));
            var second = pool.submit(() -> concurrentReserve(ready, start, secondOrder, product));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both reservation requests should reach the start gate.");
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(5, inventory.get(product).onHandQuantity());
        assertEquals(5, inventory.get(product).reservedQuantity());
        assertEquals(0, inventory.get(product).availableQuantity());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE status = 'RESERVED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE status = 'REJECTED'", Integer.class));
        assertEquals(1, outboxCount("InventoryReserved"));
        assertEquals(1, outboxCount("InventoryRejected"));
    }

    @Test
    void transactionRollsBackStockReservationInboxAndReservationWhenOutboxInsertFails() {
        UUID product = UUID.randomUUID();
        stock(product, 4);
        jdbc.execute("CREATE FUNCTION fail_inventory_reserved_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.message_type = 'InventoryReserved' THEN RAISE EXCEPTION 'forced outbox failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_inventory_reserved_outbox_trigger BEFORE INSERT ON inventory_outbox FOR EACH ROW EXECUTE FUNCTION fail_inventory_reserved_outbox()");
        UUID orderId = UUID.randomUUID();
        CommandEnvelope command = envelope("ReserveInventory", orderId);

        assertThrows(RuntimeException.class, () -> commands.reserve(command, reserve(orderId, line(product, 3))));

        assertEquals(4, inventory.get(product).availableQuantity());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM processed_messages WHERE message_id = ?", Integer.class, command.messageId()));
        assertEquals(0, outboxCount("InventoryReserved"));
    }

    @Test
    void confirmConsumesStockAndReleaseReturnsIt() {
        UUID product = UUID.randomUUID();
        stock(product, 8);
        UUID committedOrder = UUID.randomUUID();
        commands.reserve(envelope("ReserveInventory", committedOrder), reserve(committedOrder, line(product, 3)));
        UUID reservationId = reservationId(committedOrder);
        CommandEnvelope confirm = envelope("ConfirmReservation", committedOrder);
        commands.confirm(confirm, new io.fulfillx.inventory.messaging.ReservationCommandPayload(committedOrder, reservationId));
        commands.confirm(confirm, new io.fulfillx.inventory.messaging.ReservationCommandPayload(committedOrder, reservationId));
        assertEquals(5, inventory.get(product).onHandQuantity());
        assertEquals(0, inventory.get(product).reservedQuantity());
        assertEquals("COMMITTED", reservationStatus(committedOrder));
        assertEquals(1, outboxCount("InventoryCommitted"));

        UUID releasedOrder = UUID.randomUUID();
        commands.reserve(envelope("ReserveInventory", releasedOrder), reserve(releasedOrder, line(product, 2)));
        commands.release(envelope("ReleaseReservation", releasedOrder),
                new io.fulfillx.inventory.messaging.ReservationCommandPayload(releasedOrder, reservationId(releasedOrder)));
        assertEquals(5, inventory.get(product).onHandQuantity());
        assertEquals(0, inventory.get(product).reservedQuantity());
        assertEquals("RELEASED", reservationStatus(releasedOrder));
        assertEquals(1, outboxCount("ReservationReleased"));
    }

    @Test
    void commitInvariantFailurePublishesRejectionWithoutChangingReservation() {
        UUID product = UUID.randomUUID();
        stock(product, 5);
        UUID orderId = UUID.randomUUID();
        commands.reserve(envelope("ReserveInventory", orderId), reserve(orderId, line(product, 2)));
        UUID reservationId = reservationId(orderId);
        jdbc.update("UPDATE inventory_items SET reserved_quantity = 0 WHERE product_id = ?", product);

        commands.confirm(envelope("ConfirmReservation", orderId), new io.fulfillx.inventory.messaging.ReservationCommandPayload(orderId, reservationId));

        assertEquals("RESERVED", reservationStatus(orderId));
        assertEquals(5, inventory.get(product).onHandQuantity());
        assertEquals("InventoryCommitRejected", jdbc.queryForObject("SELECT message_type FROM inventory_outbox WHERE message_type = 'InventoryCommitRejected'", String.class));
        assertEquals(1, outboxCount("InventoryCommitRejected"));
    }

    @Test
    void snsTimeoutLeavesOutboxRetryableAndLaterDeliveryMarksItPublished() {
        UUID product = UUID.randomUUID();
        stock(product, 2);
        UUID orderId = UUID.randomUUID();
        commands.reserve(envelope("ReserveInventory", orderId), reserve(orderId, line(product, 1)));
        SnsClient sns = mock(SnsClient.class);
        InventoryOutboxPublisher publisher = new InventoryOutboxPublisher(outbox, sns,
                new InventoryOutboxProperties(true, 1000, 10, 30, 1),
                new InventoryMessagingProperties(false, "fulfillx-inventory-commands", "arn:aws:sns:us-east-1:000000000000:topic", "topic", 2, 30), new com.fasterxml.jackson.databind.ObjectMapper());

        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SNS publish must happen outside a database transaction.");
            throw SnsException.builder().message("simulated timeout").build();
        }).when(sns).publish(any(PublishRequest.class));
        publisher.publishPending();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbox WHERE published_at IS NOT NULL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM inventory_outbox WHERE message_type = 'InventoryReserved'", Integer.class));

        jdbc.update("UPDATE inventory_outbox SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SNS publish must happen outside a database transaction.");
            return PublishResponse.builder().messageId("aws-message-id").build();
        }).when(sns).publish(any(PublishRequest.class));
        publisher.publishPending();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbox WHERE published_at IS NOT NULL", Integer.class));
        verify(sns, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void stockApiRequiresOperationsRoleForReadAndWrite() throws Exception {
        UUID product = UUID.randomUUID();
        mvc.perform(put("/api/v1/inventory/{productId}", product).contentType("application/json").content("{\"onHandQuantity\":7}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(put("/api/v1/inventory/{productId}", product).with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS")))
                        .contentType("application/json").content("{\"onHandQuantity\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(7));
        mvc.perform(get("/api/v1/inventory/{productId}", product))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onHandQuantity").value(7));
    }

    private void concurrentReserve(CountDownLatch ready, CountDownLatch start, UUID orderId, UUID product) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent start.");
            commands.reserve(envelope("ReserveInventory", orderId), reserve(orderId, line(product, 5)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private void stock(UUID productId, long onHand) { inventory.setStock(productId, new SetStockRequest(onHand)); }
    private static ReserveInventoryPayload reserve(UUID order, ReserveInventoryPayload.Line... lines) { return new ReserveInventoryPayload(order, List.of(lines)); }
    private static ReserveInventoryPayload.Line line(UUID product, long quantity) { return new ReserveInventoryPayload.Line(product, quantity); }
    private static CommandEnvelope envelope(String type, UUID orderId) {
        UUID messageId = UUID.randomUUID();
        return new CommandEnvelope(messageId, type, 1, Instant.now(), UUID.randomUUID(), messageId, "Order", orderId, null);
    }
    private int outboxCount(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_outbox WHERE message_type = ?", Integer.class, type); }
    private String reservationStatus(UUID order) { return jdbc.queryForObject("SELECT status FROM reservations WHERE order_id = ?", String.class, order); }
    private UUID reservationId(UUID order) { return jdbc.queryForObject("SELECT reservation_id FROM reservations WHERE order_id = ?", UUID.class, order); }
}


