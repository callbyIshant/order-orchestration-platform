package io.fulfillx.order.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fulfillx.aws")
public record OrderAwsProperties(String region, String endpointUrl, String accessKeyId, String secretAccessKey,
                                 String inventoryQueue, String paymentQueue, String orderResultsQueue,
                                 String domainEventsTopicArn) { }
