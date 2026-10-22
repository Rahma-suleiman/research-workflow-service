package com.zanzibar.workflow.service;

import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowManagementServiceTest {

    private final WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
    private final WorkflowStageConfigRepository stages = mock(WorkflowStageConfigRepository.class);
    private final WorkflowValidationService validation = new WorkflowValidationService(definitions, stages);
    private final WorkflowManagementService service = new WorkflowManagementService(definitions, stages, validation);

    private final UUID draftId = UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e");

    @Test
    void publishingOverlappingDifferentWorkflowKeyIsRejectedInsteadOfArchivingIt() {
        WorkflowDefinition draft = workflow(draftId, "new-standard", WorkflowStatus.DRAFT);
        WorkflowDefinition existing = workflow(
            UUID.fromString("5c2d11a4-0d8e-41e9-b2b2-36b2cda99e7f"),
            "existing-all-types", WorkflowStatus.PUBLISHED);
        when(definitions.findById(draftId)).thenReturn(Optional.of(draft));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(draftId))
            .thenReturn(List.of(stage("DRAFT", "SUBMITTED", "SUBMIT", 1)));
        when(definitions.findByStatusAndActiveTrue(WorkflowStatus.PUBLISHED))
            .thenReturn(List.of(existing));

        WorkflowException error = assertThrows(WorkflowException.class,
            () -> service.publishWorkflow(draftId));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(definitions, never()).save(existing);
    }

    @Test
    void publishingRejectsEnabledTransitionThatCannotBeReachedFromDraft() {
        WorkflowDefinition draft = workflow(draftId, "research-permit-standard", WorkflowStatus.DRAFT);
        when(definitions.findById(draftId)).thenReturn(Optional.of(draft));
        when(stages.findByWorkflowDefinition_IdOrderByStageOrderAsc(draftId))
            .thenReturn(List.of(stage("ORPHAN_STATUS", "SOMEWHERE", "ORPHAN_ACTION", 1)));

        WorkflowException error = assertThrows(WorkflowException.class,
            () -> service.publishWorkflow(draftId));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        verify(definitions, never()).save(any());
    }

    private WorkflowDefinition workflow(UUID id, String key, WorkflowStatus status) {
        WorkflowDefinition workflow = mock(WorkflowDefinition.class);
        when(workflow.getId()).thenReturn(id);
        when(workflow.getWorkflowKey()).thenReturn(key);
        when(workflow.getStatus()).thenReturn(status);
        when(workflow.getScope()).thenReturn(WorkflowScope.SELECTED_TYPES);
        when(workflow.getApplicableApplicationTypes()).thenReturn(Set.of(
            "FOREIGN_RESEARCHER", "LOCAL_RESEARCHER",
            "INSTITUTION_RESEARCH", "INSTITUTION_COMPANY"));
        when(workflow.getWorkflowVersion()).thenReturn(2);
        when(workflow.getStages()).thenReturn(List.of());
        return workflow;
    }

    private WorkflowStageConfig stage(String from, String to, String action, int order) {
        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setName(action);
        stage.setFromStatus(from);
        stage.setToStatus(to);
        stage.setActionKey(action);
        stage.setAssignedRole("APPLICANT");
        stage.setTriggerType(TriggerType.HUMAN);
        stage.setStageOrder(order);
        stage.setSlaDays(1);
        stage.setEnabled(true);
        return stage;
    }
}
