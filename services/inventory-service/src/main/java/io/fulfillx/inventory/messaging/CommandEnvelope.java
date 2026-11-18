package io.fulfillx.inventory.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record CommandEnvelope(UUID messageId, String messageType, int schemaVersion, Instant occurredAt,
                              UUID correlationId, UUID causationId, String aggregateType, UUID aggregateId,
                              JsonNode payload) { }
