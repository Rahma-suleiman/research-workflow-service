package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.WorkflowStatus;

import java.util.List;
import java.util.UUID;

/**
 * Read-only diagnostic report. A publishable result means the configured
 * graph passed these checks; it is not a guarantee that the calling system
 * uses the same status/action contract.
 */
public record WorkflowValidationResponse(
    UUID workflowDefinitionId,
    String workflowKey,
    Integer workflowVersion,
    WorkflowStatus workflowStatus,
    boolean publishable,
    int totalTransitions,
    int enabledTransitions,
    List<String> errors,
    List<String> warnings
) {}
