package io.fulfillx.inventory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.inventory.application.InventoryCommandService;
import io.fulfillx.inventory.domain.InventoryException;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandRouter {
    private final ObjectMapper mapper;
    private final InventoryCommandService service;
    public InventoryCommandRouter(ObjectMapper mapper, InventoryCommandService service) { this.mapper = mapper; this.service = service; }

    public void handle(String body) {
        try {
            CommandEnvelope envelope = mapper.readValue(body, CommandEnvelope.class);
            if (envelope.payload() == null) throw new InventoryException("INVALID_COMMAND", "Command payload is required.");
            switch (envelope.messageType()) {
                case "ReserveInventory" -> service.reserve(envelope, mapper.treeToValue(envelope.payload(), ReserveInventoryPayload.class));
                case "ConfirmReservation" -> service.confirm(envelope, mapper.treeToValue(envelope.payload(), ReservationCommandPayload.class));
                case "ReleaseReservation" -> service.release(envelope, mapper.treeToValue(envelope.payload(), ReservationCommandPayload.class));
                default -> throw new InventoryException("UNKNOWN_COMMAND", "Unsupported Inventory command type.");
            }
        } catch (JsonProcessingException ex) {
            throw new InventoryException("INVALID_COMMAND", "Command envelope or payload is malformed.");
        }
    }
}
