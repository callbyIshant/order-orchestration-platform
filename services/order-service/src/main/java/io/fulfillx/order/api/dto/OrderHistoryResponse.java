package io.fulfillx.order.api.dto;

import io.fulfillx.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(UUID historyId, int sequence, String eventType, OrderStatus fromStatus,
                                   OrderStatus toStatus, String detailCode, Instant occurredAt) { }
