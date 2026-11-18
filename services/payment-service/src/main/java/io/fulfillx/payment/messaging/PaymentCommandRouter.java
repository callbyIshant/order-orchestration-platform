package io.fulfillx.payment.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import io.fulfillx.payment.application.PaymentCommandService;
@Component
public class PaymentCommandRouter {
 private final ObjectMapper mapper;private final PaymentCommandService service;
 public PaymentCommandRouter(ObjectMapper mapper,PaymentCommandService service){this.mapper=mapper;this.service=service;}
 public void handle(String body){try{PaymentEnvelope e=mapper.readValue(body,PaymentEnvelope.class);switch(e.messageType()){case "AuthorizePayment"->service.authorize(e);case "RefundPayment"->service.refund(e);default->throw new IllegalArgumentException("Unsupported Payment command type.");}}catch(java.io.IOException ex){throw new IllegalArgumentException("Malformed payment command.",ex);}}
}
