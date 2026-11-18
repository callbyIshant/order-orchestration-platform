package io.fulfillx.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.order.domain.IdempotencyRecord;
import io.fulfillx.order.domain.Order;
import io.fulfillx.order.domain.OrderException;
import io.fulfillx.order.domain.OrderStatus;
import io.fulfillx.order.persistence.IdempotencyRepository;
import io.fulfillx.order.persistence.OrderHistoryStore;
import io.fulfillx.order.persistence.OrderOutboxStore;
import io.fulfillx.order.persistence.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.opentelemetry.api.OpenTelemetry;
import io.fulfillx.observability.TraceContextCarrier;

@Service
public class OrderCreationPersistenceService {
    public record Item(UUID id, String name, java.math.BigDecimal price, int quantity) { }
    public record Result(Order order, boolean replay) { }
    private final JdbcTemplate jdbc; private final OrderRepository orders; private final IdempotencyRepository keys;
    private final OrderHistoryStore history; private final OrderOutboxStore outbox; private final ObjectMapper json; private final OrderViewMapper views; private final OpenTelemetry telemetry;
    public OrderCreationPersistenceService(JdbcTemplate jdbc, OrderRepository orders, IdempotencyRepository keys, OrderHistoryStore history, OrderOutboxStore outbox, ObjectMapper json, OrderViewMapper views, OpenTelemetry telemetry) {
        this.jdbc=jdbc; this.orders=orders; this.keys=keys; this.history=history; this.outbox=outbox; this.json=json; this.views=views; this.telemetry=telemetry;
    }
    @Transactional
    public Result create(String customer, String key, String fingerprint, String token, List<Item> items) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, customer.length()+"#"+customer+key);
                statement.execute();
            }
            return null;
        });
        Instant now=Instant.now();
        var existing=keys.findWithOrderForUpdate(customer,key);
        if(existing.isPresent() && existing.get().getExpiresAt().isAfter(now)) {
            if(!existing.get().getFingerprint().equals(fingerprint)) throw new OrderException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key is active for a different request.",409);
            return new Result(existing.get().getOrder(),true);
        }
        var order=new Order(UUID.randomUUID(),customer,items.stream().map(i->i.price().multiply(java.math.BigDecimal.valueOf(i.quantity()))).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add),token);
        items.forEach(i->order.addItem(i.id(),i.name(),i.price(),i.quantity()));
        orders.saveAndFlush(order);
        history.append(order,"ORDER_ACCEPTED",null, OrderStatus.PENDING,null);
        if(existing.isPresent()) existing.get().replace(fingerprint,order,now,now.plus(Duration.ofHours(24)));
        else keys.save(new IdempotencyRecord(UUID.randomUUID(),customer,key,fingerprint,order,now,now.plus(Duration.ofHours(24))));
        append(order,"reserve:"+order.getId(),"ReserveInventory","SQS", "fulfillx-inventory-commands", Map.of("orderId",order.getId(),"items",items.stream().map(i->Map.of("productId",i.id(),"quantity",i.quantity())).toList()));
        return new Result(order,false);
    }
    private void append(Order order,String dedupe,String type,String destinationType,String destination,Object payload) {
        try { var id=UUID.randomUUID(); var envelope=Map.of("messageId",id,"messageType",type,"schemaVersion",1,"occurredAt",Instant.now(),"correlationId",order.getId(),"causationId",order.getId(),"aggregateType","Order","aggregateId",order.getId(),"payload",payload,"traceContext",TraceContextCarrier.capture(telemetry));
            outbox.append(id,dedupe,order.getId(),type,destinationType,destination,json.writeValueAsString(envelope));
        } catch(Exception ex){throw new IllegalStateException("Could not encode order command",ex);}
    }
}
