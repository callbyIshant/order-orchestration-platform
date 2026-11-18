package io.fulfillx.catalog.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Product fields. All unit prices are denominated in INR.")
public record CreateProductRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        @Schema(example = "MUG-003")
        String sku,

        @NotBlank
        @Size(max = 200)
        @Schema(example = "Insulated Travel Mug")
        String name,

        @Size(max = 2000)
        @Schema(example = "Double-wall stainless steel, 400 ml.")
        String description,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 10, fraction = 2)
        @Schema(example = "799.00", minimum = "0.00")
        BigDecimal unitPrice,

        @NotNull
        @Schema(example = "10000000-0000-0000-0000-000000000001")
        UUID categoryId,

        @Schema(description = "Defaults to true when omitted.", example = "true")
        Boolean active
) {
}
