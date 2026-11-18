package io.fulfillx.inventory.messaging;

import java.util.UUID;

public record ReservationCommandPayload(UUID orderId, UUID reservationId) { }
