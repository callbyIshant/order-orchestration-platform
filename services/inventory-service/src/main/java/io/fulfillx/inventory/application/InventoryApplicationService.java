package io.fulfillx.inventory.application;

import io.fulfillx.inventory.api.dto.InventoryResponse;
import io.fulfillx.inventory.api.dto.SetStockRequest;
import io.fulfillx.inventory.domain.InventoryException;
import io.fulfillx.inventory.domain.InventoryItem;
import io.fulfillx.inventory.persistence.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryApplicationService {
    private final InventoryItemRepository items;
    public InventoryApplicationService(InventoryItemRepository items) { this.items = items; }

    @Transactional(readOnly = true)
    public InventoryResponse get(UUID productId) {
        InventoryItem item = items.findById(productId).orElseThrow(() -> new InventoryException("INVENTORY_NOT_FOUND", "No inventory record exists for product " + productId + "."));
        return InventoryResponse.from(item);
    }

    @Transactional
    public InventoryResponse setStock(UUID productId, SetStockRequest request) {
        InventoryItem item = items.findByProductIdForUpdate(productId).orElse(null);
        if (item == null) {
            item = items.saveAndFlush(new InventoryItem(productId, request.onHandQuantity()));
        } else {
            item.setOnHandQuantity(request.onHandQuantity());
            items.flush();
        }
        return InventoryResponse.from(item);
    }
}
