
package com.zanzibar.workflow.dto.response;

import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record WorkflowResponse(
    UUID id,
    String workflowKey,
    String name,
    String description,
    WorkflowScope scope,
    WorkflowStatus status,
    Integer workflowVersion,
    boolean active,
    Set<String> applicableApplicationTypes,
    List<WorkflowStageResponse> stages,
    Instant createdAt,
    Instant updatedAt,
    Instant publishedAt
) {}
