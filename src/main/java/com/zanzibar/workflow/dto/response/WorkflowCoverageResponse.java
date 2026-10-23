package com.zanzibar.workflow.dto.response;

import java.util.List;
import java.util.UUID;

/** Resolution coverage for each active registered subject type. */
public record WorkflowCoverageResponse(
    boolean allSubjectTypesCovered,
    List<SubjectTypeCoverage> subjectTypes
) {
    public record SubjectTypeCoverage(
        String subjectType,
        String result,
        int matchingWorkflowCount,
        UUID workflowDefinitionId,
        String workflowKey,
        Integer workflowVersion,
        String message
    ) {}
}
