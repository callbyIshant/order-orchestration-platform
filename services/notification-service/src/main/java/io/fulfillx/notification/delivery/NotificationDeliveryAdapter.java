package io.fulfillx.notification.delivery;
import io.fulfillx.notification.domain.NotificationMessage;
public interface NotificationDeliveryAdapter { void deliver(NotificationMessage message); }
