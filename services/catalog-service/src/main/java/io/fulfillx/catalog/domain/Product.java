package io.fulfillx.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku")
)
public class Product {

    private static final Pattern SKU_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "unit_price_inr", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceInr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_products_category")
    )
    private Category category;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(
            UUID id,
            String sku,
            String name,
            String description,
            BigDecimal unitPriceInr,
            Category category,
            boolean active
    ) {
        if (id == null) {
            throw new CatalogDomainException("INVALID_PRODUCT", "Product id is required.");
        }
        this.id = id;
        this.sku = normalizeSku(sku);
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.unitPriceInr = normalizePrice(unitPriceInr);
        this.category = requireCategory(category);
        this.active = active;
    }

    public static String normalizeSku(String value) {
        if (value == null) {
            throw new CatalogDomainException("INVALID_SKU", "SKU is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SKU_PATTERN.matcher(normalized).matches()) {
            throw new CatalogDomainException(
                    "INVALID_SKU",
                    "SKU must be 1-64 characters using letters, numbers, dot, underscore, or hyphen."
            );
        }
        return normalized;
    }

    public static String normalizeName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 200) {
            throw new CatalogDomainException("INVALID_PRODUCT_NAME", "Product name must be 1-200 characters.");
        }
        return value.trim();
    }

    public static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.trim().length() > 2000) {
            throw new CatalogDomainException("INVALID_DESCRIPTION", "Product description must be at most 2000 characters.");
        }
        return value.trim();
    }

    public static BigDecimal normalizePrice(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new CatalogDomainException("INVALID_UNIT_PRICE", "Unit price must be zero or greater.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new CatalogDomainException("INVALID_UNIT_PRICE", "Unit price may have at most two decimal places.");
        }
    }

    private static Category requireCategory(Category value) {
        if (value == null) {
            throw new CatalogDomainException("INVALID_CATEGORY", "Product category is required.");
        }
        return value;
    }

    public void update(String name, String description, BigDecimal unitPriceInr, Category category, Boolean active) {
        if (name != null) {
            this.name = normalizeName(name);
        }
        if (description != null) {
            this.description = normalizeDescription(description);
        }
        if (unitPriceInr != null) {
            this.unitPriceInr = normalizePrice(unitPriceInr);
        }
        if (category != null) {
            this.category = requireCategory(category);
        }
        if (active != null) {
            this.active = active;
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getUnitPriceInr() {
        return unitPriceInr;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
