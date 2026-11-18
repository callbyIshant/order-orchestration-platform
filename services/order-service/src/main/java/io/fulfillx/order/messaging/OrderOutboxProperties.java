package io.fulfillx.order.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fulfillx.outbox")
public record OrderOutboxProperties(boolean publisherEnabled, long pollMs, int batchSize, int leaseSeconds, int retrySeconds) { }
