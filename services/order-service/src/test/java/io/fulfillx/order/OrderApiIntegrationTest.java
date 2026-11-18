package io.fulfillx.order;

import io.fulfillx.order.catalog.CatalogProduct;
import io.fulfillx.order.catalog.CatalogProductClient;
import io.fulfillx.order.domain.OrderStatus;
import io.fulfillx.order.application.OrderSagaService;
import io.fulfillx.order.messaging.OrderMessageEnvelope;
import io.fulfillx.order.persistence.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers @ActiveProfiles("local")
class OrderApiIntegrationTest {
    static final UUID PRODUCT=UUID.fromString("20000000-0000-0000-0000-000000000001");
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("fulfillx.messaging.consumer-enabled",()->"false");r.add("fulfillx.outbox.publisher-enabled",()->"false");r.add("fulfillx.oidc.issuer-uri",()->"http://127.0.0.1:8080/realms/fulfillx");r.add("fulfillx.oidc.jwk-set-uri",()->"http://127.0.0.1:8080/realms/fulfillx/protocol/openid-connect/certs");}
    @Autowired MockMvc mvc; @Autowired OrderRepository orders; @Autowired OrderSagaService saga; @Autowired ObjectMapper mapper; @MockitoBean CatalogProductClient catalog;
    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor customer(){return jwt().jwt(j->j.subject("customer-1")).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"));}
    String body(int quantity){return "{\"items\":[{\"productId\":\""+PRODUCT+"\",\"quantity\":"+quantity+"}],\"paymentToken\":\"tok_success\"}";}
    @Test void acceptsOrderAndSnapshotsProductThenReplaysActiveKey() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenReturn(new CatalogProduct(PRODUCT,"Mug",new BigDecimal("12.50"),"INR",true));
        var first=mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","demo-1").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
        String id=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(first.getResponse().getContentAsString()).get("orderId").asText();
        mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","demo-1").contentType(MediaType.APPLICATION_JSON).content(body(2)))
                .andExpect(status().isAccepted()).andExpect(header().string("Idempotency-Replayed","true")).andExpect(jsonPath("$.orderId").value(id));
        mvc.perform(get("/api/v1/orders/"+id).with(customer())).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Mug")).andExpect(jsonPath("$.totalAmount").value(25.0));
        org.junit.jupiter.api.Assertions.assertEquals(1,orders.count());verify(catalog,times(1)).getOrderableProduct(PRODUCT);
    }
    @Test void rejectsPayloadMismatchForActiveKey() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenReturn(new CatalogProduct(PRODUCT,"Mug",new BigDecimal("12.50"),"INR",true));
        mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","same-key").contentType(MediaType.APPLICATION_JSON).content(body(1))).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","same-key").contentType(MediaType.APPLICATION_JSON).content(body(3))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }
    @Test void rejectsInvalidProductWithoutPersistingOrder() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenThrow(new io.fulfillx.order.catalog.CatalogClientException(io.fulfillx.order.catalog.CatalogClientException.Kind.NOT_ORDERABLE,"Inactive product",null));
        mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","bad-product").contentType(MediaType.APPLICATION_JSON).content(body(1))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_ORDERABLE"));
        org.junit.jupiter.api.Assertions.assertEquals(0,orders.count());
    }
    @Test void requiresValidCustomerJwtAndRejectsOtherRoles() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "no-token").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders").header("Authorization", "Bearer invalid")
                        .header("Idempotency-Key", "bad-token").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/orders").with(jwt().jwt(j -> j.subject("staff"))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS")))
                        .header("Idempotency-Key", "wrong-role").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isForbidden());
    }
    @Test void customerCannotReadAnotherCustomersOrder() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenReturn(new CatalogProduct(PRODUCT,"Mug",new BigDecimal("12.50"),"INR",true));
        var accepted=mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","owner-check").contentType(MediaType.APPLICATION_JSON).content(body(1)))
                .andExpect(status().isAccepted()).andReturn();
        String orderId=mapper.readTree(accepted.getResponse().getContentAsString()).get("orderId").asText();
        mvc.perform(get("/api/v1/orders/"+orderId).with(jwt().jwt(j->j.subject("customer-2"))
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
    @Test void duplicateEventsAreIgnoredAndPaymentDeclineReleasesReservation() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenReturn(new CatalogProduct(PRODUCT,"Mug",new BigDecimal("12.50"),"INR",true));
        var accepted=mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","compensate").contentType(MediaType.APPLICATION_JSON).content(body(1))).andExpect(status().isAccepted()).andReturn();
        UUID orderId=UUID.fromString(mapper.readTree(accepted.getResponse().getContentAsString()).get("orderId").asText()); UUID reservation=UUID.randomUUID(); UUID inventoryEvent=UUID.randomUUID();
        saga.accept(event(inventoryEvent,"InventoryReserved",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString())));
        saga.accept(event(inventoryEvent,"InventoryReserved",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString())));
        saga.accept(event(UUID.randomUUID(),"PaymentDeclined",orderId,mapper.createObjectNode().put("orderId",orderId.toString()).put("reasonCode","PAYMENT_DECLINED")));
        saga.accept(event(UUID.randomUUID(),"ReservationReleased",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString())));
        mvc.perform(get("/api/v1/orders/"+orderId).with(customer())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.name()));
    }
    @Test void commitRejectionWaitsForRefundAndReleaseBeforeCancelling() throws Exception {
        when(catalog.getOrderableProduct(PRODUCT)).thenReturn(new CatalogProduct(PRODUCT,"Mug",new BigDecimal("12.50"),"INR",true));
        var accepted=mvc.perform(post("/api/v1/orders").with(customer()).header("Idempotency-Key","refund-release").contentType(MediaType.APPLICATION_JSON).content(body(1))).andExpect(status().isAccepted()).andReturn();
        UUID orderId=UUID.fromString(mapper.readTree(accepted.getResponse().getContentAsString()).get("orderId").asText()); UUID reservation=UUID.randomUUID(); UUID payment=UUID.randomUUID();
        saga.accept(event(UUID.randomUUID(),"InventoryReserved",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString())));
        saga.accept(event(UUID.randomUUID(),"PaymentAuthorized",payment,mapper.createObjectNode().put("orderId",orderId.toString()).put("paymentId",payment.toString()).put("amount",12.50).put("currency","INR")));
        saga.accept(event(UUID.randomUUID(),"InventoryCommitRejected",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString()).put("reasonCode","RESERVATION_INVARIANT_VIOLATION")));
        saga.accept(event(UUID.randomUUID(),"PaymentRefunded",payment,mapper.createObjectNode().put("orderId",orderId.toString()).put("paymentId",payment.toString()).put("amount",12.50).put("currency","INR")));
        mvc.perform(get("/api/v1/orders/"+orderId).with(customer())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(OrderStatus.COMPENSATION_PENDING.name()));
        saga.accept(event(UUID.randomUUID(),"ReservationReleased",reservation,mapper.createObjectNode().put("orderId",orderId.toString()).put("reservationId",reservation.toString())));
        mvc.perform(get("/api/v1/orders/"+orderId).with(customer())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.name()));
    }
    private OrderMessageEnvelope event(UUID messageId,String type,UUID aggregate,com.fasterxml.jackson.databind.JsonNode payload){return new OrderMessageEnvelope(messageId,type,1,java.time.Instant.now(),UUID.fromString(payload.path("orderId").asText()),UUID.randomUUID(),type.startsWith("Payment")?"PaymentTransaction":"InventoryReservation",aggregate,payload);}
}
