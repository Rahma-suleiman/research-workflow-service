
package com.zanzibar.workflow.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Webhook details without its signing secret. */
public record WebhookResponse(
    UUID id,
    String url,
    List<String> eventTypes,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {}
