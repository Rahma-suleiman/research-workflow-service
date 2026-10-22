
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.TriggerType;

import java.util.UUID;

public record WorkflowStageResponse(
    UUID id,
    String name,
    String description,
    String fromStatus,
    String toStatus,
    String actionKey,
    String assignedRole,
    TriggerType triggerType,
    Integer stageOrder,
    Integer slaDays,
    boolean requiresComments,
    boolean externalApproval,
    String externalSystemId,
    boolean enabled
) {}
