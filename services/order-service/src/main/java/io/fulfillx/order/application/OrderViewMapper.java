package io.fulfillx.order.application;

import io.fulfillx.order.api.dto.OrderAcceptedResponse;
import io.fulfillx.order.api.dto.OrderItemResponse;
import io.fulfillx.order.api.dto.OrderResponse;
import io.fulfillx.order.domain.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderViewMapper {
    public OrderResponse response(Order order) {
        return new OrderResponse(order.getId(), order.getStatus(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getTotalAmount(), order.getCurrency(),
                order.getCreatedAt(), order.getUpdatedAt());
    }
    public OrderAcceptedResponse accepted(Order order) {
        return new OrderAcceptedResponse(order.getId(), order.getStatus(), order.getCreatedAt(), "/api/v1/orders/" + order.getId());
    }
}
