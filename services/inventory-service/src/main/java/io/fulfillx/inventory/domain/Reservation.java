package io.fulfillx.inventory.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(name = "uk_reservations_order_id", columnNames = "order_id"))
public class Reservation {
    @Id
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReservationStatus status;
    @Column(name = "rejection_code", length = 64)
    private String rejectionCode;
    @Column(name = "rejection_product_id")
    private UUID rejectionProductId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ElementCollection
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "reservation_id"))
    @OrderBy("productId ASC")
    private List<ReservationLine> lines;

    protected Reservation() { }
    public Reservation(UUID reservationId, UUID orderId, String requestHash, List<ReservationLine> lines, ReservationStatus status) {
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.requestHash = requestHash;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public void reject(String code, UUID productId) { status = ReservationStatus.REJECTED; rejectionCode = code; rejectionProductId = productId; updatedAt = Instant.now(); }
    public void setStatus(ReservationStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public UUID getReservationId() { return reservationId; }
    public UUID getOrderId() { return orderId; }
    public String getRequestHash() { return requestHash; }
    public ReservationStatus getStatus() { return status; }
    public String getRejectionCode() { return rejectionCode; }
    public UUID getRejectionProductId() { return rejectionProductId; }
    public List<ReservationLine> getLines() { return lines; }
}
