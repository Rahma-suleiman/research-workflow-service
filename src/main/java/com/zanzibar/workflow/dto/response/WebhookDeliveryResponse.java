
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
    UUID id,
    UUID eventId,
    DeliveryStatus status,
    int attempts,
    Instant nextAttemptAt,
    Integer lastStatusCode,
    String lastError,
    Instant deliveredAt,
    Instant createdAt
) {}
