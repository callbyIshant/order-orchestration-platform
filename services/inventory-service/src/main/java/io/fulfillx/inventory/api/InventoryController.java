package io.fulfillx.inventory.api;

import io.fulfillx.inventory.api.dto.InventoryResponse;
import io.fulfillx.inventory.api.dto.SetStockRequest;
import io.fulfillx.inventory.application.InventoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryApplicationService inventory;
    public InventoryController(InventoryApplicationService inventory) { this.inventory = inventory; }

    @GetMapping("/{productId}")
    public InventoryResponse get(@PathVariable UUID productId) { return inventory.get(productId); }

    @PutMapping("/{productId}")
    public ResponseEntity<InventoryResponse> setStock(@PathVariable UUID productId, @Valid @RequestBody SetStockRequest request) {
        return ResponseEntity.ok(inventory.setStock(productId, request));
    }
}
