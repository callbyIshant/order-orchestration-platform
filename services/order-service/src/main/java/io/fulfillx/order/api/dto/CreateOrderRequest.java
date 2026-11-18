package io.fulfillx.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @NotBlank @Pattern(regexp = "tok_success|tok_decline") String paymentToken
) {
    public record Item(@NotNull UUID productId, @Positive int quantity) { }
}
