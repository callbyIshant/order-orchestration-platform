package io.fulfillx.inventory.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.inventory.domain.InventoryException;
import io.fulfillx.inventory.domain.InventoryItem;
import io.fulfillx.inventory.domain.Reservation;
import io.fulfillx.inventory.domain.ReservationLine;
import io.fulfillx.inventory.domain.ReservationStatus;
import io.fulfillx.inventory.messaging.CommandEnvelope;
import io.fulfillx.inventory.messaging.OutboxEventFactory;
import io.fulfillx.inventory.messaging.ReservationCommandPayload;
import io.fulfillx.inventory.messaging.ReserveInventoryPayload;
import io.fulfillx.inventory.persistence.InventoryItemRepository;
import io.fulfillx.inventory.persistence.OutboxStore;
import io.fulfillx.inventory.persistence.ProcessedMessageStore;
import io.fulfillx.inventory.persistence.ReservationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class InventoryCommandService {
    private final InventoryItemRepository items;
    private final ReservationRepository reservations;
    private final ProcessedMessageStore processed;
    private final OutboxStore outbox;
    private final OutboxEventFactory eventFactory;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public InventoryCommandService(InventoryItemRepository items, ReservationRepository reservations,
                                   ProcessedMessageStore processed, OutboxStore outbox,
                                   OutboxEventFactory eventFactory, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.items = items;
        this.reservations = reservations;
        this.processed = processed;
        this.outbox = outbox;
        this.eventFactory = eventFactory;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public void reserve(CommandEnvelope envelope, ReserveInventoryPayload command) {
        validateEnvelope(envelope, "ReserveInventory", command.orderId());
        Map<UUID, Long> lines = normalize(command.items());
        lockOrder(command.orderId());
        if (!processed.insertIfAbsent(envelope.messageId(), envelope.messageType())) return;
        String requestHash = hash(lines);
        Reservation existing = reservations.findByOrderIdForUpdate(command.orderId()).orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new InventoryException("ORDER_PAYLOAD_MISMATCH", "An order ID cannot be reused with different reservation lines.");
            }
            return;
        }

        Map<UUID, InventoryItem> lockedItems = new TreeMap<>();
        for (UUID productId : lines.keySet()) {
            items.findByProductIdForUpdate(productId).ifPresent(item -> lockedItems.put(productId, item));
        }
        UUID rejectedProduct = null;
        String rejection = null;
        for (Map.Entry<UUID, Long> line : lines.entrySet()) {
            InventoryItem item = lockedItems.get(line.getKey());
            if (item == null || item.availableQuantity() < line.getValue()) {
                rejectedProduct = line.getKey();
                rejection = item == null ? "PRODUCT_NOT_STOCKED" : "INSUFFICIENT_STOCK";
                break;
            }
        }
        Reservation reservation = new Reservation(UUID.randomUUID(), command.orderId(), requestHash,
                toReservationLines(lines), rejectedProduct == null ? ReservationStatus.RESERVED : ReservationStatus.REJECTED);
        if (rejectedProduct == null) {
            for (Map.Entry<UUID, Long> line : lines.entrySet()) lockedItems.get(line.getKey()).reserve(line.getValue());
            reservations.save(reservation);
            appendEvent("InventoryReserved", reservation, envelope, Map.of("orderId", command.orderId(), "reservationId", reservation.getReservationId()));
        } else {
            reservation.reject(rejection, rejectedProduct);
            reservations.save(reservation);
            appendEvent("InventoryRejected", reservation, envelope, Map.of(
                    "orderId", command.orderId(), "reservationId", reservation.getReservationId(),
                    "reasonCode", rejection, "productId", rejectedProduct));
        }
    }

    @Transactional
    public void confirm(CommandEnvelope envelope, ReservationCommandPayload command) {
        transition(envelope, command, "ConfirmReservation", ReservationStatus.COMMITTED, "InventoryCommitted");
    }

    @Transactional
    public void release(CommandEnvelope envelope, ReservationCommandPayload command) {
        transition(envelope, command, "ReleaseReservation", ReservationStatus.RELEASED, "ReservationReleased");
    }

    private void transition(CommandEnvelope envelope, ReservationCommandPayload command, String commandType,
                            ReservationStatus target, String eventType) {
        validateEnvelope(envelope, commandType, command.orderId());
        if (command.reservationId() == null) throw new InventoryException("INVALID_COMMAND", "Reservation id is required.");
        if (!processed.insertIfAbsent(envelope.messageId(), envelope.messageType())) return;
        Reservation reservation = reservations.findByOrderIdForUpdate(command.orderId())
                .orElseThrow(() -> new InventoryException("RESERVATION_NOT_FOUND", "No reservation exists for the requested order."));
        if (!reservation.getReservationId().equals(command.reservationId())) throw new InventoryException("RESERVATION_ID_MISMATCH", "Reservation id does not match the order reservation.");
        if (reservation.getStatus() == target) return;
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new InventoryException("INVALID_RESERVATION_STATE", "Reservation cannot transition from " + reservation.getStatus() + " to " + target + ".");
        }
        List<ReservationLine> orderedLines = reservation.getLines().stream().sorted((a, b) -> a.getProductId().compareTo(b.getProductId())).toList();
        Map<UUID, InventoryItem> lockedItems = new TreeMap<>();
        for (ReservationLine line : orderedLines) {
            var item = items.findByProductIdForUpdate(line.getProductId()).orElse(null);
            if (item == null && target == ReservationStatus.COMMITTED) {
                appendEvent("InventoryCommitRejected", reservation, envelope, Map.of("orderId", command.orderId(), "reservationId", reservation.getReservationId(), "reasonCode", "INVENTORY_MISSING", "reservationState", "RESERVED"));
                return;
            }
            if (item == null) throw new InventoryException("INVENTORY_NOT_FOUND", "Inventory disappeared for a reserved product.");
            if (target == ReservationStatus.COMMITTED && item.getReservedQuantity() < line.getQuantity()) {
                appendEvent("InventoryCommitRejected", reservation, envelope, Map.of("orderId", command.orderId(), "reservationId", reservation.getReservationId(), "reasonCode", "RESERVATION_INVARIANT_VIOLATION", "reservationState", "RESERVED"));
                return;
            }
            lockedItems.put(line.getProductId(), item);
        }
        for (ReservationLine line : orderedLines) {
            InventoryItem item = lockedItems.get(line.getProductId());
            if (target == ReservationStatus.COMMITTED) item.commit(line.getQuantity()); else item.release(line.getQuantity());
        }
        reservation.setStatus(target);
        reservations.save(reservation);
        appendEvent(eventType, reservation, envelope, Map.of("orderId", command.orderId(), "reservationId", reservation.getReservationId()));
    }

    private void appendEvent(String eventType, Reservation reservation, CommandEnvelope command, Map<String, Object> payload) {
        try {
            UUID eventId = UUID.randomUUID();
            String body = objectMapper.writeValueAsString(eventFactory.create(eventId, eventType, reservation.getReservationId(),
                    command.correlationId(), command.messageId(), payload));
            outbox.append(eventId, reservation.getReservationId(), eventType, body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize Inventory result event.", ex);
        }
    }

    private void lockOrder(UUID orderId) {
        // A transaction-scoped advisory lock serializes distinct messages for one order even if malformed payloads name disjoint products.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, orderId.toString());
                statement.execute();
            }
            return null;
        });
    }

    private static void validateEnvelope(CommandEnvelope envelope, String expectedType, UUID orderId) {
        if (envelope == null || envelope.messageId() == null || envelope.schemaVersion() != 1 || !expectedType.equals(envelope.messageType())
                || envelope.occurredAt() == null || envelope.correlationId() == null || envelope.causationId() == null
                || orderId == null || !"Order".equals(envelope.aggregateType()) || !orderId.equals(envelope.aggregateId())) {
            throw new InventoryException("INVALID_COMMAND", "Command envelope is invalid or inconsistent with its payload.");
        }
    }

    private static Map<UUID, Long> normalize(List<ReserveInventoryPayload.Line> input) {
        if (input == null || input.isEmpty()) throw new InventoryException("INVALID_COMMAND", "At least one inventory line is required.");
        Map<UUID, Long> merged = new TreeMap<>();
        for (ReserveInventoryPayload.Line line : input) {
            if (line == null || line.productId() == null || line.quantity() <= 0) throw new InventoryException("INVALID_COMMAND", "Each product line needs an id and positive quantity.");
            try { merged.merge(line.productId(), line.quantity(), Math::addExact); }
            catch (ArithmeticException ex) { throw new InventoryException("INVALID_COMMAND", "Combined product quantity exceeds the supported range."); }
        }
        return merged;
    }

    private static List<ReservationLine> toReservationLines(Map<UUID, Long> lines) {
        List<ReservationLine> result = new ArrayList<>();
        lines.forEach((productId, quantity) -> result.add(new ReservationLine(productId, quantity)));
        return result;
    }

    private static String hash(Map<UUID, Long> lines) {
        try {
            String canonical = lines.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).reduce((a, b) -> a + "\n" + b).orElse("");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
