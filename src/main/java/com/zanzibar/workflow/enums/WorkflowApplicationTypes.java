package com.zanzibar.workflow.enums;

import java.util.Set;

/**
 * Supported application-type contract for the current Research Permit system.
 * Update this list deliberately when the permit backend introduces a new type.
 */
public final class WorkflowApplicationTypes {

    public static final Set<String> SUPPORTED = Set.of(
        "FOREIGN_RESEARCHER",
        "LOCAL_RESEARCHER",
        "INSTITUTION_RESEARCH",
        "INSTITUTION_COMPANY"
    );

    private WorkflowApplicationTypes() {
    }
}
