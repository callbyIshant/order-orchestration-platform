package io.fulfillx.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.INVENTORY_RESERVED, OrderStatus.REJECTED),
            OrderStatus.INVENTORY_RESERVED, Set.of(OrderStatus.PAYMENT_AUTHORIZED, OrderStatus.COMPENSATION_PENDING),
            OrderStatus.PAYMENT_AUTHORIZED, Set.of(OrderStatus.CONFIRMED, OrderStatus.COMPENSATION_PENDING),
            OrderStatus.COMPENSATION_PENDING, Set.of(OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(), OrderStatus.REJECTED, Set.of(), OrderStatus.CANCELLED, Set.of()
    );

    @Id @Column(name = "order_id", nullable = false)
    private UUID id;
    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "payment_token", length = 32)
    private String paymentToken;
    @Column(name = "payment_id")
    private UUID paymentId;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "requires_refund", nullable = false)
    private boolean requiresRefund;
    @Column(name = "refund_completed", nullable = false)
    private boolean refundCompleted;
    @Column(name = "requires_release", nullable = false)
    private boolean requiresRelease;
    @Column(name = "release_completed", nullable = false)
    private boolean releaseCompleted;
    @Version @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() { }
    public Order(UUID id, String customerId, BigDecimal totalAmount, String paymentToken) {
        this.id = id;
        this.customerId = customerId;
        this.status = OrderStatus.PENDING;
        this.totalAmount = totalAmount.setScale(2);
        this.currency = "INR";
        this.paymentToken = paymentToken;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
    public void addItem(UUID productId, String name, BigDecimal unitPrice, int quantity) {
        items.add(new OrderItem(UUID.randomUUID(), this, productId, name, unitPrice.setScale(2), quantity));
    }
    public void transitionTo(OrderStatus target) {
        if (!TRANSITIONS.get(status).contains(target)) throw new OrderException("INVALID_STATE_TRANSITION", "Order cannot transition from " + status + " to " + target + ".", 409);
        status = target;
        updatedAt = Instant.now();
    }
    public void setReservationId(UUID reservationId) { this.reservationId = reservationId; }
    public void authorize(UUID paymentId) { this.paymentId = paymentId; this.paymentToken = null; }
    public void clearPaymentToken() { this.paymentToken = null; }
    public void beginCompensation(boolean refund, boolean release) {
        this.requiresRefund = refund; this.requiresRelease = release;
        this.refundCompleted = !refund; this.releaseCompleted = !release;
    }
    public boolean markRefundCompleted() { refundCompleted = true; return compensationComplete(); }
    public boolean markReleaseCompleted() { releaseCompleted = true; return compensationComplete(); }
    private boolean compensationComplete() { return (!requiresRefund || refundCompleted) && (!requiresRelease || releaseCompleted); }
    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getPaymentToken() { return paymentToken; }
    public UUID getPaymentId() { return paymentId; }
    public UUID getReservationId() { return reservationId; }
    public boolean isRequiresRefund() { return requiresRefund; }
    public boolean isRefundCompleted() { return refundCompleted; }
    public boolean isRequiresRelease() { return requiresRelease; }
    public boolean isReleaseCompleted() { return releaseCompleted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
}
