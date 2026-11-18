package io.fulfillx.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;
    @Column(name = "reserved_quantity", nullable = false)
    private long reservedQuantity;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItem() { }
    public InventoryItem(UUID productId, long onHandQuantity) {
        if (productId == null) throw new InventoryException("INVALID_PRODUCT_ID", "Product id is required.");
        this.productId = productId;
        setOnHandQuantity(onHandQuantity);
        this.reservedQuantity = 0;
        this.updatedAt = Instant.now();
    }

    public void setOnHandQuantity(long quantity) {
        if (quantity < 0) throw new InventoryException("INVALID_QUANTITY", "On-hand quantity cannot be negative.");
        if (quantity < reservedQuantity) throw new InventoryException("STOCK_BELOW_RESERVED", "On-hand stock cannot be below the quantity already reserved.");
        this.onHandQuantity = quantity;
        this.updatedAt = Instant.now();
    }
    public void reserve(long quantity) {
        if (quantity <= 0 || availableQuantity() < quantity) throw new InventoryException("INSUFFICIENT_STOCK", "Requested quantity exceeds available stock.");
        reservedQuantity = Math.addExact(reservedQuantity, quantity);
        updatedAt = Instant.now();
    }
    public void release(long quantity) {
        if (quantity <= 0 || reservedQuantity < quantity) throw new InventoryException("INVALID_RESERVATION", "Reserved quantity is inconsistent.");
        reservedQuantity -= quantity;
        updatedAt = Instant.now();
    }
    public void commit(long quantity) {
        if (quantity <= 0 || reservedQuantity < quantity || onHandQuantity < quantity) throw new InventoryException("INVALID_RESERVATION", "Reserved quantity is inconsistent.");
        reservedQuantity -= quantity;
        onHandQuantity -= quantity;
        updatedAt = Instant.now();
    }
    @PreUpdate protected void beforeUpdate() { updatedAt = Instant.now(); }
    public UUID getProductId() { return productId; }
    public long getOnHandQuantity() { return onHandQuantity; }
    public long getReservedQuantity() { return reservedQuantity; }
    public long availableQuantity() { return onHandQuantity - reservedQuantity; }
    public Instant getUpdatedAt() { return updatedAt; }
}
