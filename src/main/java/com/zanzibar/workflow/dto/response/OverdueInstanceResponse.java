
package com.zanzibar.workflow.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OverdueInstanceResponse(
    UUID instanceId,
    String subjectType,
    String subjectId,
    String currentStatus,
    Instant statusEnteredAt,
    Instant dueAt,
    boolean breachNotified
) {}
