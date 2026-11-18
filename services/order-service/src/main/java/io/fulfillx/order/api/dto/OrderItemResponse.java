package io.fulfillx.order.api.dto;

import io.fulfillx.order.domain.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getProductName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }
}
