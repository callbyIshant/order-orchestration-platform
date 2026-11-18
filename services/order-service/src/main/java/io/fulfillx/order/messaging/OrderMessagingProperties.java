package io.fulfillx.order.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fulfillx.messaging")
public record OrderMessagingProperties(boolean consumerEnabled, int visibilityBaseSeconds, int visibilityMaxSeconds) { }
