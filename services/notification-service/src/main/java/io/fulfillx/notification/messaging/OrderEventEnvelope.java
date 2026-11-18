package io.fulfillx.notification.messaging;
import com.fasterxml.jackson.databind.JsonNode;import java.time.Instant;import java.util.UUID;
public record OrderEventEnvelope(UUID messageId,String messageType,int schemaVersion,Instant occurredAt,UUID correlationId,UUID causationId,String aggregateType,UUID aggregateId,JsonNode payload) { }
