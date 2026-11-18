package io.fulfillx.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_customer_key", columnNames = {"customer_id", "idempotency_key"}))
public class IdempotencyRecord {
    @Id @Column(name = "idempotency_record_id", nullable = false)
    private UUID id;
    @Column(name = "customer_id", nullable = false, length = 255)
    private String customerId;
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String key;
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String fingerprint;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IdempotencyRecord() { }
    public IdempotencyRecord(UUID id, String customerId, String key, String fingerprint, Order order, Instant now, Instant expiresAt) {
        this.id = id; this.customerId = customerId; this.key = key; this.fingerprint = fingerprint;
        this.order = order; this.createdAt = now; this.updatedAt = now; this.expiresAt = expiresAt;
    }
    public void replace(String fingerprint, Order order, Instant now, Instant expiresAt) {
        this.fingerprint = fingerprint; this.order = order; this.updatedAt = now; this.expiresAt = expiresAt;
    }
    public String getFingerprint() { return fingerprint; }
    public Order getOrder() { return order; }
    public Instant getExpiresAt() { return expiresAt; }
}
