package io.fulfillx.order.api.dto;

import io.fulfillx.order.domain.Order;
import io.fulfillx.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, OrderStatus status, List<OrderItemResponse> items, BigDecimal totalAmount,
                            String currency, Instant createdAt, Instant updatedAt) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(), order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(), order.getCurrency(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
