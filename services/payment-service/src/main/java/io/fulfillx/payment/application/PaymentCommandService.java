package io.fulfillx.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.payment.messaging.PaymentEnvelope;
import io.fulfillx.payment.persistence.PaymentOutboxStore;
import io.fulfillx.payment.persistence.PaymentStore;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import io.fulfillx.observability.TraceContextCarrier;
import io.opentelemetry.api.OpenTelemetry;

@Service
public class PaymentCommandService {
 private final PaymentStore payments; private final PaymentOutboxStore outbox; private final JdbcTemplate jdbc; private final ObjectMapper json; private final OpenTelemetry telemetry;
 public PaymentCommandService(PaymentStore payments,PaymentOutboxStore outbox,JdbcTemplate jdbc,ObjectMapper json,OpenTelemetry telemetry){this.payments=payments;this.outbox=outbox;this.jdbc=jdbc;this.json=json;this.telemetry=telemetry;}

 @Transactional public void authorize(PaymentEnvelope e){UUID order=validate(e,"AuthorizePayment");JsonNode p=e.payload();fields(p,"orderId","amount","currency","paymentToken");BigDecimal amount=amount(p);String currency=required(p,"currency");if(amount.signum()<0||!"INR".equals(currency))throw new IllegalArgumentException("Invalid payment amount or currency.");String token=required(p,"paymentToken");if(!token.matches("tok_[A-Za-z0-9_]{1,40}"))throw new IllegalArgumentException("Only opaque fake payment tokens are accepted.");String status="tok_success".equals(token)?"AUTHORIZED":"DECLINED";String decision="tok_success".equals(token)?"AUTHORIZED":"tok_decline".equals(token)?"PAYMENT_DECLINED":"INVALID_TOKEN";
  lock("order:"+order);if(!payments.accept(e.messageId(),e.messageType()))return;var existing=payments.byOrderForUpdate(order);if(existing!=null){if(existing.amount().compareTo(amount)!=0||!existing.currency().equals(currency)||!existing.status().equals(status)||!existing.decision().equals(decision))throw new IllegalArgumentException("Conflicting authorization command for an existing order.");return;}
  UUID id=UUID.randomUUID();var payment=new PaymentStore.Payment(id,order,amount,currency,status,decision);payments.insert(payment);emit(e,payment,status.equals("AUTHORIZED")?"PaymentAuthorized":"PaymentDeclined",status.equals("AUTHORIZED")?Map.of("orderId",order,"paymentId",id,"amount",amount,"currency",currency):Map.of("orderId",order,"paymentId",id,"reasonCode",decision,"amount",amount,"currency",currency));
 }
 @Transactional public void refund(PaymentEnvelope e){UUID order=validate(e,"RefundPayment");JsonNode p=e.payload();fields(p,"orderId","paymentId","amount","currency","reasonCode");UUID id=uuid(p,"paymentId");BigDecimal amount=amount(p);String currency=required(p,"currency");required(p,"reasonCode");lock("payment:"+id);if(!payments.accept(e.messageId(),e.messageType()))return;var payment=payments.byIdForUpdate(id);if(payment==null||!payment.orderId().equals(order))throw new IllegalArgumentException("Refund references an unknown payment for this order.");if(payment.amount().compareTo(amount)!=0||!payment.currency().equals(currency))throw new IllegalArgumentException("Refund amount or currency does not match the authorization.");if(payment.status().equals("REFUNDED"))return;if(!payment.status().equals("AUTHORIZED"))throw new IllegalArgumentException("Only an authorized payment can be refunded.");payments.updateStatus(id,"REFUNDED","REFUNDED");emit(e,new PaymentStore.Payment(id,order,amount,currency,"REFUNDED","REFUNDED"),"PaymentRefunded",Map.of("orderId",order,"paymentId",id,"amount",amount,"currency",currency));
 }
 private UUID validate(PaymentEnvelope e,String expected){if(e==null||e.messageId()==null||e.messageId().equals(new UUID(0,0))||!expected.equals(e.messageType())||e.schemaVersion()!=1||e.occurredAt()==null||e.correlationId()==null||e.causationId()==null||!"Order".equals(e.aggregateType())||e.aggregateId()==null||e.payload()==null)throw new IllegalArgumentException("Invalid payment command envelope.");UUID order=uuid(e.payload(),"orderId");if(!order.equals(e.aggregateId())||!order.equals(e.correlationId()))throw new IllegalArgumentException("Order aggregate/correlation does not match command payload.");return order;}
 private void emit(PaymentEnvelope cause,PaymentStore.Payment payment,String type,Object payload){try{UUID id=UUID.randomUUID();var event=Map.of("messageId",id,"messageType",type,"schemaVersion",1,"occurredAt",Instant.now(),"correlationId",cause.correlationId(),"causationId",cause.messageId(),"aggregateType","PaymentTransaction","aggregateId",payment.id(),"payload",payload,"traceContext",TraceContextCarrier.capture(telemetry));outbox.append(id,"event:"+type+":"+payment.id(),payment.id(),type,json.writeValueAsString(event));}catch(Exception ex){throw new IllegalStateException("Could not serialize payment result",ex);}}
 private void lock(String key){jdbc.execute((ConnectionCallback<Void>)c->{try(var s=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){s.setString(1,key);s.execute();}return null;});}
 private static String required(JsonNode p,String field){if(p==null||!p.hasNonNull(field)||p.path(field).asText().isBlank())throw new IllegalArgumentException("Missing required field: "+field);return p.path(field).asText();}
 private static void fields(JsonNode p,String... allowed){var names=java.util.Set.of(allowed);var it=p.fieldNames();while(it.hasNext())if(!names.contains(it.next()))throw new IllegalArgumentException("Unsupported payment command field.");}
 private static BigDecimal amount(JsonNode p){try{return new BigDecimal(required(p,"amount"));}catch(NumberFormatException ex){throw new IllegalArgumentException("Invalid amount.");}}
 private static UUID uuid(JsonNode p,String field){try{return UUID.fromString(required(p,field));}catch(Exception ex){throw new IllegalArgumentException("Invalid UUID field: "+field);}}
}
