package io.fulfillx.order.api.dto;

import io.fulfillx.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderAcceptedResponse(UUID orderId, OrderStatus status, Instant createdAt, String statusUrl) { }
