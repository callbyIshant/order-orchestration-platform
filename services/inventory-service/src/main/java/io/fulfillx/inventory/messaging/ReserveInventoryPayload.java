package io.fulfillx.inventory.messaging;

import java.util.List;
import java.util.UUID;

public record ReserveInventoryPayload(UUID orderId, List<Line> items) {
    public record Line(UUID productId, long quantity) { }
}
