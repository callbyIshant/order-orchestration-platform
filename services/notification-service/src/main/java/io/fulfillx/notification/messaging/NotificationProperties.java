package io.fulfillx.notification.messaging;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("notification")
public record NotificationProperties(Messaging messaging,long pollMs,int visibilityBaseSeconds,int visibilityMaxSeconds,Delivery delivery){public record Messaging(boolean consumerEnabled,String queueName){}public record Delivery(String mode,String from,int maxAttempts){}}
