
package com.zanzibar.workflow.dto.response;

import java.util.UUID;

public record TransitionResponse(
    UUID workflowDefinitionId,
    Integer workflowVersion,
    String subjectId,
    String fromStatus,
    String toStatus,
    String actionKey,
    String decision,
    String message,
    UUID transitionLogId
) {}
