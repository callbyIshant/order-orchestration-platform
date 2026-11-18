package io.fulfillx.inventory.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("inventory.messaging")
public record InventoryMessagingProperties(boolean consumerEnabled, String queueName, String topicArn, String topicName,
                                           int visibilityBaseSeconds, int visibilityMaxSeconds) { }
