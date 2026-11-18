package io.fulfillx.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_categories_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_categories_slug", columnNames = "slug")
        }
)
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    protected Category() {
    }

    public Category(UUID id, String name, String slug) {
        if (id == null) {
            throw new CatalogDomainException("INVALID_CATEGORY", "Category id is required.");
        }
        this.id = id;
        this.name = requireText(name, "Category name is required.", 100);
        this.slug = requireText(slug, "Category slug is required.", 120);
    }

    private static String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new CatalogDomainException("INVALID_CATEGORY", message);
        }
        return value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
