package io.fulfillx.inventory.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;

public record SetStockRequest(@NotNull @PositiveOrZero Long onHandQuantity) { }
