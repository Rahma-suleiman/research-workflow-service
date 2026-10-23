package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.response.WorkflowCoverageResponse;
import com.zanzibar.workflow.dto.response.WorkflowValidationResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowValidationServiceTest {

    private final WorkflowDefinitionRepository definitions =
        mock(WorkflowDefinitionRepository.class);
    private final WorkflowStageConfigRepository stages =
        mock(WorkflowStageConfigRepository.class);
    private final WorkflowValidationService service =
        new WorkflowValidationService(definitions, stages, TestRegistries.registry(
            List.of("APPLICANT", "REVIEWER"),
            List.of("TYPE_A", "TYPE_B", "TYPE_C")));

    private static final String TENANT = "DEFAULT";

    private final UUID workflowId =
        UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e");

    @Test
    void validReachableDraftGraphIsPublishable() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("DRAFT", "SUBMITTED", "SUBMIT", "APPLICANT",
                    TriggerType.HUMAN, 1),
                stage("SUBMITTED", "PAYMENT_PENDING", "INITIATE_PAYMENT", "SYSTEM",
                    TriggerType.EXTERNAL_EVENT, 2)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertTrue(report.publishable(), report.errors().toString());
        assertEquals(2, report.totalTransitions());
        assertEquals(2, report.enabledTransitions());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void unreachableEnabledTransitionPreventsPublication() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("DRAFT", "SUBMITTED", "SUBMIT", "APPLICANT",
                    TriggerType.HUMAN, 1),
                stage("ORPHAN", "DONE", "ORPHAN_ACTION", "APPLICANT",
                    TriggerType.HUMAN, 2)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertFalse(report.publishable());
        assertTrue(report.errors().stream().anyMatch(
            error -> error.contains("unreachable from DRAFT")));
    }

    @Test
    void systemRoleCannotBeCombinedWithHumanRoleForExternalEventTransition() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("DRAFT", "SUBMITTED", "SUBMIT", "APPLICANT",
                    TriggerType.HUMAN, 1),
                stage("SUBMITTED", "PAID", "PAY", "SYSTEM,APPLICANT",
                    TriggerType.EXTERNAL_EVENT, 2)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertFalse(report.publishable());
        assertTrue(report.errors().stream().anyMatch(
            error -> error.contains("must be assigned to SYSTEM")));
    }

    @Test
    void coverageReportsMissingAndAmbiguousTypes() {
        WorkflowDefinition first = coverageWorkflow(
            "standard-flow", WorkflowScope.SELECTED_TYPES,
            Set.of("TYPE_A", "TYPE_B"));
        WorkflowDefinition duplicate = coverageWorkflow(
            "another-workflow", WorkflowScope.SELECTED_TYPES, Set.of("TYPE_A"));

        when(definitions.findByTenantKeyAndStatusAndActiveTrue(TENANT, WorkflowStatus.PUBLISHED))
            .thenReturn(List.of(first, duplicate));

        WorkflowCoverageResponse report = service.inspectSubjectTypeCoverage(TENANT);

        assertFalse(report.allSubjectTypesCovered());
        assertEquals("AMBIGUOUS", report.subjectTypes().stream()
            .filter(item -> item.subjectType().equals("TYPE_A"))
            .findFirst().orElseThrow().result());
        assertEquals("RESOLVED", report.subjectTypes().stream()
            .filter(item -> item.subjectType().equals("TYPE_B"))
            .findFirst().orElseThrow().result());
        assertEquals("MISSING", report.subjectTypes().stream()
            .filter(item -> item.subjectType().equals("TYPE_C"))
            .findFirst().orElseThrow().result());
    }

    @Test
    void reachabilityStartsFromTheWorkflowsOwnInitialStatus() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(workflow.getInitialStatus()).thenReturn("NEW");
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("NEW", "OPEN", "OPEN_TICKET", "APPLICANT", TriggerType.HUMAN, 1),
                stage("OPEN", "CLOSED", "CLOSE", "REVIEWER", TriggerType.HUMAN, 2)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertTrue(report.publishable(), report.errors().toString());
    }

    @Test
    void graphThatDoesNotStartAtInitialStatusIsRejected() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(workflow.getInitialStatus()).thenReturn("NEW");
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("DRAFT", "SUBMITTED", "SUBMIT", "APPLICANT", TriggerType.HUMAN, 1)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertFalse(report.publishable());
        assertTrue(report.errors().stream().anyMatch(
            error -> error.contains("starts from the initial status NEW")));
    }

    @Test
    void unregisteredRolePreventsPublication() {
        WorkflowDefinition workflow = workflow(WorkflowStatus.DRAFT, false);
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(workflowId))
            .thenReturn(List.of(
                stage("DRAFT", "SUBMITTED", "SUBMIT", "AUDITOR", TriggerType.HUMAN, 1)
            ));

        WorkflowValidationResponse report = service.validateWorkflow(TENANT, workflowId);

        assertFalse(report.publishable());
        assertTrue(report.errors().stream().anyMatch(
            error -> error.contains("unknown or inactive role 'AUDITOR'")));
    }

    private WorkflowDefinition workflow(WorkflowStatus status, boolean active) {
        WorkflowDefinition mockWorkflow = mock(WorkflowDefinition.class);
        when(mockWorkflow.getId()).thenReturn(workflowId);
        when(mockWorkflow.getTenantKey()).thenReturn(TENANT);
        when(mockWorkflow.getWorkflowKey()).thenReturn("standard-flow");
        when(mockWorkflow.getWorkflowVersion()).thenReturn(2);
        when(mockWorkflow.getStatus()).thenReturn(status);
        when(mockWorkflow.isActive()).thenReturn(active);
        when(mockWorkflow.getScope()).thenReturn(WorkflowScope.SELECTED_TYPES);
        when(mockWorkflow.getSubjectTypes()).thenReturn(Set.of("TYPE_A", "TYPE_B"));
        when(mockWorkflow.getInitialStatus()).thenReturn("DRAFT");
        return mockWorkflow;
    }

    private WorkflowDefinition coverageWorkflow(
        String key,
        WorkflowScope scope,
        Set<String> types
    ) {
        WorkflowDefinition workflow = mock(WorkflowDefinition.class);
        when(workflow.getId()).thenReturn(UUID.randomUUID());
        when(workflow.getWorkflowKey()).thenReturn(key);
        when(workflow.getWorkflowVersion()).thenReturn(2);
        when(workflow.getStatus()).thenReturn(WorkflowStatus.PUBLISHED);
        when(workflow.isActive()).thenReturn(true);
        when(workflow.getScope()).thenReturn(scope);
        when(workflow.getSubjectTypes()).thenReturn(types);
        return workflow;
    }

    private WorkflowStageConfig stage(
        String from,
        String to,
        String action,
        String role,
        TriggerType trigger,
        int order
    ) {
        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setName(action);
        stage.setFromStatus(from);
        stage.setToStatus(to);
        stage.setActionKey(action);
        stage.setAssignedRole(role);
        stage.setTriggerType(trigger);
        stage.setStageOrder(order);
        stage.setEnabled(true);
        return stage;
    }
}
