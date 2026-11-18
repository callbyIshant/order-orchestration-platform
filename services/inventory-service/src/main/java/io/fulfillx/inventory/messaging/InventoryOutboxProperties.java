package io.fulfillx.inventory.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("inventory.outbox")
public record InventoryOutboxProperties(boolean publisherEnabled, long pollMs, int batchSize, int leaseSeconds, int retrySeconds) { }
