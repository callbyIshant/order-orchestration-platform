package io.fulfillx.order.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogProduct(UUID id, String name, BigDecimal unitPrice, String currency, boolean active) { }
