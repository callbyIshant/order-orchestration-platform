package io.fulfillx.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class ReservationLine {
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "quantity", nullable = false)
    private long quantity;
    protected ReservationLine() { }
    public ReservationLine(UUID productId, long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
    public UUID getProductId() { return productId; }
    public long getQuantity() { return quantity; }
}
