package io.fulfillx.order.messaging;

import java.util.UUID;

public record OutboxRecord(UUID messageId, String deduplicationKey, String messageType, String destinationType,
                           String destination, String payload, int attempts, UUID leaseToken) { }
