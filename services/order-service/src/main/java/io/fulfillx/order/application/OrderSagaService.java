package io.fulfillx.order.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.order.domain.Order;
import io.fulfillx.order.domain.OrderException;
import io.fulfillx.order.domain.OrderStatus;
import io.fulfillx.order.messaging.OrderMessageEnvelope;
import io.fulfillx.order.persistence.OrderHistoryStore;
import io.fulfillx.order.persistence.OrderOutboxStore;
import io.fulfillx.order.persistence.OrderRepository;
import io.fulfillx.order.persistence.ProcessedOrderMessageStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.opentelemetry.api.OpenTelemetry;
import io.fulfillx.observability.TraceContextCarrier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

@Service
public class OrderSagaService {
    private final OrderRepository orders; private final ProcessedOrderMessageStore inbox; private final OrderHistoryStore history; private final OrderOutboxStore outbox; private final ObjectMapper json; private final OpenTelemetry telemetry; private final MeterRegistry meters;
    public OrderSagaService(OrderRepository orders,ProcessedOrderMessageStore inbox,OrderHistoryStore history,OrderOutboxStore outbox,ObjectMapper json,OpenTelemetry telemetry,MeterRegistry meters){this.orders=orders;this.inbox=inbox;this.history=history;this.outbox=outbox;this.json=json;this.telemetry=telemetry;this.meters=meters;}
    @Transactional
    public void accept(OrderMessageEnvelope message) {
        if(message.messageId()==null || message.messageType()==null || message.schemaVersion()!=1 || message.occurredAt()==null || message.correlationId()==null || message.causationId()==null || message.aggregateType()==null || message.aggregateId()==null || message.payload()==null) throw new OrderException("INVALID_EVENT","Required event envelope fields are missing or unsupported.",400);
        if((message.messageType().startsWith("Inventory")||message.messageType().equals("ReservationReleased"))&& !"InventoryReservation".equals(message.aggregateType())) throw new OrderException("INVALID_EVENT","Inventory event aggregate type is invalid.",400);
        if(message.messageType().startsWith("Payment")&& !"PaymentTransaction".equals(message.aggregateType())) throw new OrderException("INVALID_EVENT","Payment event aggregate type is invalid.",400);
        if(!message.messageType().startsWith("Inventory")&&!message.messageType().equals("ReservationReleased")&&!message.messageType().startsWith("Payment"))throw new OrderException("UNSUPPORTED_EVENT","Unsupported saga event type.",400);
        UUID orderId=message.payload().hasNonNull("orderId")?uuid(message.payload(),"orderId"):message.aggregateId();
        if(orderId==null) throw new OrderException("INVALID_EVENT","Saga event does not identify its order.",400);
        if(!orderId.equals(message.correlationId())) throw new OrderException("INVALID_EVENT","Saga event correlation does not match its order.",400);
        Order order=orders.findByIdForUpdate(orderId).orElseThrow(()->new OrderException("ORDER_NOT_FOUND","Saga event references an unknown order.",404));
        if(!inbox.insertIfAbsent(message.messageId(),message.messageType())) return;
        JsonNode p=message.payload(); String type=message.messageType();
        switch(type) {
            case "InventoryReserved" -> { if(order.getStatus()!=OrderStatus.PENDING) return; UUID reservation=uuid(p,"reservationId"); if(!reservation.equals(message.aggregateId())) throw new OrderException("INVALID_EVENT","Reservation event aggregate does not match payload.",400); order.setReservationId(reservation); transition(order,OrderStatus.INVENTORY_RESERVED,type,null); command(order,message,"authorize:"+order.getId(),"AuthorizePayment","fulfillx-payment-commands",Map.of("orderId",order.getId(),"amount",order.getTotalAmount(),"currency",order.getCurrency(),"paymentToken",order.getPaymentToken())); }
            case "InventoryRejected" -> { if(order.getStatus()!=OrderStatus.PENDING) return; String reason=requiredText(p,"reasonCode"); transition(order,OrderStatus.REJECTED,type,reason); event(order,message,"OrderRejected",Map.of("orderId",order.getId(),"reasonCode",reason)); }
            case "PaymentAuthorized" -> { if(order.getStatus()!=OrderStatus.INVENTORY_RESERVED) return; UUID payment=uuid(p,"paymentId"); if(!payment.equals(message.aggregateId())||!p.hasNonNull("amount")||order.getTotalAmount().compareTo(p.path("amount").decimalValue())!=0||!order.getCurrency().equals(text(p,"currency"))) throw new OrderException("INVALID_EVENT","Payment authorization does not match the order.",400); order.authorize(payment); transition(order,OrderStatus.PAYMENT_AUTHORIZED,type,null); command(order,message,"confirm:"+order.getId(),"ConfirmReservation","fulfillx-inventory-commands",Map.of("orderId",order.getId(),"reservationId",order.getReservationId())); }
            case "PaymentDeclined" -> { if(order.getStatus()!=OrderStatus.INVENTORY_RESERVED) return; String reason=requiredText(p,"reasonCode"); order.clearPaymentToken(); order.beginCompensation(false,true); transition(order,OrderStatus.COMPENSATION_PENDING,type,reason); command(order,message,"release:"+order.getId(),"ReleaseReservation","fulfillx-inventory-commands",Map.of("orderId",order.getId(),"reservationId",order.getReservationId())); }
            case "InventoryCommitted" -> { if(order.getStatus()!=OrderStatus.PAYMENT_AUTHORIZED) return; if(!uuid(p,"reservationId").equals(order.getReservationId())||!order.getReservationId().equals(message.aggregateId())) throw new OrderException("INVALID_EVENT","Commit event reservation does not match the order.",400); transition(order,OrderStatus.CONFIRMED,type,null); event(order,message,"OrderConfirmed",Map.of("orderId",order.getId(),"amount",order.getTotalAmount(),"currency",order.getCurrency())); }
            case "InventoryCommitRejected" -> { if(order.getStatus()!=OrderStatus.PAYMENT_AUTHORIZED) return; String reason=requiredText(p,"reasonCode"); if(!uuid(p,"reservationId").equals(order.getReservationId())||!order.getReservationId().equals(message.aggregateId())) throw new OrderException("INVALID_EVENT","Commit rejection reservation does not match the order.",400); order.beginCompensation(true,true); transition(order,OrderStatus.COMPENSATION_PENDING,type,reason); command(order,message,"refund:"+order.getId(),"RefundPayment","fulfillx-payment-commands",Map.of("orderId",order.getId(),"paymentId",order.getPaymentId(),"amount",order.getTotalAmount(),"currency",order.getCurrency(),"reasonCode",reason)); command(order,message,"release:"+order.getId(),"ReleaseReservation","fulfillx-inventory-commands",Map.of("orderId",order.getId(),"reservationId",order.getReservationId())); }
            case "ReservationReleased" -> { if(order.getStatus()!=OrderStatus.COMPENSATION_PENDING) return; if(!uuid(p,"reservationId").equals(order.getReservationId())||!order.getReservationId().equals(message.aggregateId())) throw new OrderException("INVALID_EVENT","Release event reservation does not match the order.",400); if(order.markReleaseCompleted()) finishCancelled(order,type,message); else history.append(order,type,order.getStatus(),order.getStatus(),null); }
            case "PaymentRefunded" -> { if(order.getStatus()!=OrderStatus.COMPENSATION_PENDING) return; if(!uuid(p,"paymentId").equals(order.getPaymentId())||!order.getPaymentId().equals(message.aggregateId())||!p.hasNonNull("amount")||order.getTotalAmount().compareTo(p.path("amount").decimalValue())!=0||!order.getCurrency().equals(text(p,"currency"))) throw new OrderException("INVALID_EVENT","Refund event payment does not match the order.",400); if(order.markRefundCompleted()) finishCancelled(order,type,message); else history.append(order,type,order.getStatus(),order.getStatus(),null); }
            default -> throw new OrderException("UNSUPPORTED_EVENT","Unsupported saga event type.",400);
        }
    }
    private void finishCancelled(Order order,String type,OrderMessageEnvelope cause){transition(order,OrderStatus.CANCELLED,type,null);event(order,cause,"OrderCancelled",Map.of("orderId",order.getId(),"reasonCode","SAGA_COMPENSATED"));}
    private void transition(Order order,OrderStatus next,String type,String detail){OrderStatus old=order.getStatus();order.transitionTo(next);history.append(order,type,old,next,detail);if(next==OrderStatus.CONFIRMED||next==OrderStatus.REJECTED||next==OrderStatus.CANCELLED){meters.counter("fulfillx.order.outcomes","outcome",next.name()).increment();Timer.builder("fulfillx.saga.duration").tag("outcome",next.name()).register(meters).record(Duration.between(order.getCreatedAt(),Instant.now()));}}
    private void command(Order order,OrderMessageEnvelope cause,String dedupe,String type,String queue,Object payload){append(order,cause,dedupe,type,"SQS",queue,payload);}
    private void event(Order order,OrderMessageEnvelope cause,String type,Object payload){append(order,cause,"event:"+type+":"+order.getId(),type,"SNS","",payload);}
    private void append(Order order,OrderMessageEnvelope cause,String dedupe,String type,String destType,String destination,Object payload){try{UUID id=UUID.randomUUID();var env=Map.of("messageId",id,"messageType",type,"schemaVersion",1,"occurredAt",Instant.now(),"correlationId",cause.correlationId(),"causationId",cause.messageId(),"aggregateType","Order","aggregateId",order.getId(),"payload",payload,"traceContext",TraceContextCarrier.capture(telemetry));outbox.append(id,dedupe,order.getId(),type,destType,destination,json.writeValueAsString(env));}catch(Exception ex){throw new IllegalStateException("Could not encode saga message",ex);}}
    private static UUID uuid(JsonNode node,String field){try{return UUID.fromString(node.path(field).asText());}catch(Exception ex){throw new OrderException("INVALID_EVENT","Saga event has invalid "+field+".",400);}}
    private static String text(JsonNode node,String field){return node.path(field).isMissingNode()?null:node.path(field).asText();}
    private static String requiredText(JsonNode node,String field){String value=text(node,field);if(value==null||value.isBlank())throw new OrderException("INVALID_EVENT","Saga event is missing "+field+".",400);return value;}
}
