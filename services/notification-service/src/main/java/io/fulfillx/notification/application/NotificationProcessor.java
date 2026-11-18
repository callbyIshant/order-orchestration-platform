package io.fulfillx.notification.application;
import io.fulfillx.notification.delivery.NotificationDeliveryAdapter;import io.fulfillx.notification.messaging.NotificationProperties;import io.fulfillx.notification.messaging.OrderEventEnvelope;import io.fulfillx.notification.persistence.NotificationStore;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.stereotype.Service;
@Service public class NotificationProcessor{
 private static final Logger log=LoggerFactory.getLogger(NotificationProcessor.class);private final NotificationStore store;private final NotificationDeliveryAdapter delivery;private final NotificationProperties properties;
 public NotificationProcessor(NotificationStore store,NotificationDeliveryAdapter delivery,NotificationProperties properties){this.store=store;this.delivery=delivery;this.properties=properties;}
 public void process(OrderEventEnvelope event){var work=store.prepare(event);if(work==null)return;try{delivery.deliver(work.message());store.complete(work,true,properties.delivery().maxAttempts());}catch(RuntimeException ex){store.complete(work,false,properties.delivery().maxAttempts());log.warn("Notification delivery failed: eventId={}, orderId={}, attempt={}",work.message().sourceMessageId(),work.message().orderId(),work.attemptNumber(),ex);throw ex;}}
}
