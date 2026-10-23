
package com.zanzibar.workflow.dto.request;

import com.zanzibar.workflow.enums.TriggerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Performs an action on a workflow instance. The engine uses the instance's
 * stored status; expectedStatus optionally rejects actions taken from a stale view.
 */
public record InstanceActionRequest(

    @Size(max = 80)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*")
    String expectedStatus,

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
