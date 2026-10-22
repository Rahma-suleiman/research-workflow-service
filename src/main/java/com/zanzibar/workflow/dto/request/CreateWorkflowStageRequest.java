
package com.zanzibar.workflow.dto.request;

import com.zanzibar.workflow.enums.TriggerType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkflowStageRequest(

    @NotBlank
    @Size(max = 200)
    String name,

    String description,

    @NotBlank
    @Size(max = 80)
    String fromStatus,

    @NotBlank
    @Size(max = 80)
    String toStatus,

    @NotBlank
    @Size(max = 100)
    String actionKey,

    @NotBlank
    @Size(max = 100)
    String assignedRole,

    @NotNull
    TriggerType triggerType,

    @NotNull
    @Min(1)
    Integer stageOrder,

    @Min(0)
    Integer slaDays,

    boolean requiresComments,

    boolean externalApproval,

    @Size(max = 200)
    String externalSystemId,

    boolean enabled

) {}
