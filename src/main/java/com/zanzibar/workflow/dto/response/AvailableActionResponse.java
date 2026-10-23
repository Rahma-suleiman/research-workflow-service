
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.TriggerType;

import java.util.List;

/** An action that can be performed from an instance's current status. */
public record AvailableActionResponse(
    String actionKey,
    String name,
    String toStatus,
    List<String> assignedRoles,
    TriggerType triggerType,
    boolean requiresComments,
    Integer slaDays
) {}
