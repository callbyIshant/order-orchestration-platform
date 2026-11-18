package io.fulfillx.order;

import io.fulfillx.order.domain.Order;
import io.fulfillx.order.domain.OrderException;
import io.fulfillx.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {
    @Test void onlyDeclaredSagaTransitionsAreAllowed(){
        var order=new Order(UUID.randomUUID(),"customer",new BigDecimal("10.00"),"tok_success");
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        assertThrows(OrderException.class,()->order.transitionTo(OrderStatus.CONFIRMED));
        order.transitionTo(OrderStatus.PAYMENT_AUTHORIZED);
        order.transitionTo(OrderStatus.COMPENSATION_PENDING);
        order.transitionTo(OrderStatus.CANCELLED);
        assertThrows(OrderException.class,()->order.transitionTo(OrderStatus.PENDING));
    }
}
