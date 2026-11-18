package io.fulfillx.catalog.api.dto;

import io.fulfillx.catalog.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Product view. All unit prices are denominated in INR.")
public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        String currency,
        CategoryResponse category,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getUnitPriceInr(),
                "INR",
                CategoryResponse.from(product.getCategory()),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
