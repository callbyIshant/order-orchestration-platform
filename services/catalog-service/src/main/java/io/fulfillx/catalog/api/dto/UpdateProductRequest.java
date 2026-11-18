package io.fulfillx.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Partial product update. Null or omitted properties are left unchanged.")
public record UpdateProductRequest(
        @Size(min = 1, max = 200)
        String name,

        @Size(max = 2000)
        @Schema(description = "Send an empty string to clear the description.")
        String description,

        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal unitPrice,

        UUID categoryId,

        Boolean active
) {

    public boolean hasChanges() {
        return name != null || description != null || unitPrice != null || categoryId != null || active != null;
    }
}
