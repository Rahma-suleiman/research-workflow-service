package com.zanzibar.workflow.dto.response;

import java.util.List;
import java.util.UUID;

/** Resolution coverage for each supported Research Permit application type. */
public record WorkflowCoverageResponse(
    boolean allApplicationTypesCovered,
    List<ApplicationTypeCoverage> applicationTypes
) {
    public record ApplicationTypeCoverage(
        String applicationType,
        String result,
        int matchingWorkflowCount,
        UUID workflowDefinitionId,
        String workflowKey,
        Integer workflowVersion,
        String message
    ) {}
}
