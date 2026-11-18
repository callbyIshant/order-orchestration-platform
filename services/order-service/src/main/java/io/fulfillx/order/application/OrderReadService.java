package io.fulfillx.order.application;

import io.fulfillx.order.api.dto.OrderHistoryResponse;
import io.fulfillx.order.api.dto.OrderResponse;
import io.fulfillx.order.domain.OrderException;
import io.fulfillx.order.persistence.OrderHistoryStore;
import io.fulfillx.order.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class OrderReadService {
    private final OrderRepository orders; private final OrderHistoryStore history; private final OrderViewMapper mapper;
    public OrderReadService(OrderRepository orders, OrderHistoryStore history, OrderViewMapper mapper) { this.orders=orders; this.history=history; this.mapper=mapper; }
    @Transactional(readOnly=true)
    public OrderResponse get(UUID id, String customer) {
        var order=orders.findByIdAndCustomerId(id, customer).orElseThrow(()->new OrderException("ORDER_NOT_FOUND", "Order was not found.",404));
        return mapper.response(order);
    }
    @Transactional(readOnly=true)
    public List<OrderHistoryResponse> history(UUID id, String customer) {
        if (!orders.existsByIdAndCustomerId(id,customer)) throw new OrderException("ORDER_NOT_FOUND", "Order was not found.",404);
        return history.findByOrderId(id);
    }
}
