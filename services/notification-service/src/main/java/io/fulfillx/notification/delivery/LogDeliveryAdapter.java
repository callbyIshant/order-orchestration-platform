package io.fulfillx.notification.delivery;
import io.fulfillx.notification.domain.NotificationMessage;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(prefix="notification.delivery",name="mode",havingValue="log")
public class LogDeliveryAdapter implements NotificationDeliveryAdapter{private static final Logger log=LoggerFactory.getLogger(LogDeliveryAdapter.class);public void deliver(NotificationMessage m){log.info("Local notification delivered: type={}, orderId={}, recipient={}, correlationId={}",m.type(),m.orderId(),m.recipient(),m.correlationId());}}
