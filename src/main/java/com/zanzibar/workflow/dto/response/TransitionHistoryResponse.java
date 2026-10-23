
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.TriggerType;

import java.time.Instant;
import java.util.UUID;

public record TransitionHistoryResponse(
    UUID id,
    String actionKey,
    String fromStatus,
    String toStatus,
    TriggerType triggerType,
    String actorId,
    String actorRole,
    String comments,
    String clientId,
    Instant executedAt
) {}
