package io.fulfillx.inventory.api.dto;

import io.fulfillx.inventory.domain.InventoryItem;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(UUID productId, long onHandQuantity, long reservedQuantity, long availableQuantity, Instant updatedAt) {
    public static InventoryResponse from(InventoryItem item) {
        return new InventoryResponse(item.getProductId(), item.getOnHandQuantity(), item.getReservedQuantity(), item.availableQuantity(), item.getUpdatedAt());
    }
}
