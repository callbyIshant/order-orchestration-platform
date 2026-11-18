package io.fulfillx.inventory.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.fulfillx.observability.TraceContextCarrier;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventFactory {
    private final OpenTelemetry telemetry;
    public OutboxEventFactory(OpenTelemetry telemetry) { this.telemetry = telemetry; }
    public Map<String, Object> create(UUID messageId, String messageType, UUID reservationId,
                                      UUID correlationId, UUID causationId, Map<String, Object> payload) {
        return Map.of(
                "messageId", messageId,
                "messageType", messageType,
                "schemaVersion", 1,
                "occurredAt", Instant.now(),
                "correlationId", correlationId == null ? messageId : correlationId,
                "causationId", causationId,
                "aggregateType", "InventoryReservation",
                "aggregateId", reservationId,
                "payload", payload,
                "traceContext", TraceContextCarrier.capture(telemetry)
        );
    }
}
