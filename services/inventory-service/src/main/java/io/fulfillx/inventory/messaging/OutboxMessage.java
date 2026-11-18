package io.fulfillx.inventory.messaging;

import java.util.UUID;

public record OutboxMessage(UUID messageId, UUID aggregateId, String messageType, String payload, int attempts, UUID leaseToken) { }
