
package com.zanzibar.workflow.dto.request;

import com.zanzibar.workflow.enums.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ExecuteTransitionRequest(

    @NotNull
    UUID workflowDefinitionId,

    @NotNull
    Integer workflowVersion,

    @NotNull
    UUID applicationId,

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String currentStatus,

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String actionKey,

    @NotNull
    TriggerType triggerType,

    @NotBlank
    @Size(max = 150)
    String actorId,

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String actorRole,

    @Size(max = 4000)
    String comments,

    @NotBlank
    @Size(max = 150)
    String idempotencyKey

) {}
