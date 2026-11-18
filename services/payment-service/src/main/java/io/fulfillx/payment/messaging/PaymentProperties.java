package io.fulfillx.payment.messaging;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("payment")
public record PaymentProperties(Messaging messaging,long pollMs,int visibilityBaseSeconds,int visibilityMaxSeconds,Outbox outbox) {
 public record Messaging(boolean consumerEnabled,String queueName,String topicArn) { }
 public record Outbox(boolean publisherEnabled,long pollMs,int batchSize,int leaseSeconds,int retrySeconds) { }
}
