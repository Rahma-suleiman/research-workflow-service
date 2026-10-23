
package com.zanzibar.workflow.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstanceResponse(
    UUID id,
    String subjectType,
    String subjectId,
    UUID workflowDefinitionId,
    String workflowKey,
    Integer workflowVersion,
    String currentStatus,
    boolean terminal,
    List<AvailableActionResponse> availableActions,
    Instant createdAt,
    Instant updatedAt
) {}
