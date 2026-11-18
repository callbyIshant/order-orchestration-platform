package io.fulfillx.notification.domain;
import java.util.UUID;
public record NotificationMessage(UUID notificationId,UUID sourceMessageId,UUID orderId,UUID correlationId,String type,String recipient,String subject,String body){ }
