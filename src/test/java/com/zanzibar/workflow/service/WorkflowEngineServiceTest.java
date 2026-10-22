package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.dto.response.TransitionResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.model.WorkflowTransitionLog;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import com.zanzibar.workflow.repository.WorkflowTransitionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowEngineServiceTest {

    private final WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
    private final WorkflowStageConfigRepository stages = mock(WorkflowStageConfigRepository.class);
    private final WorkflowTransitionLogRepository logs = mock(WorkflowTransitionLogRepository.class);
    private final WorkflowEngineService engine = new WorkflowEngineService(definitions, stages, logs);

    private final UUID workflowId = UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e");
    private final UUID applicationId = UUID.fromString("7f4e6ed8-0ed9-4b20-a2d0-222b40ad6c01");

    @BeforeEach
    void setUp() {
        when(logs.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        WorkflowDefinition workflow = mock(WorkflowDefinition.class);
        when(workflow.getId()).thenReturn(workflowId);
        when(workflow.getWorkflowVersion()).thenReturn(2);
        when(workflow.getStatus()).thenReturn(WorkflowStatus.PUBLISHED);
        when(workflow.isActive()).thenReturn(true);
        when(definitions.findById(workflowId)).thenReturn(Optional.of(workflow));

        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setFromStatus("DRAFT");
        stage.setToStatus("SUBMITTED");
        stage.setActionKey("SUBMIT");
        stage.setAssignedRole("APPLICANT");
        stage.setTriggerType(TriggerType.HUMAN);
        stage.setRequiresComments(false);
        stage.setEnabled(true);
        when(stages.findByWorkflowDefinition_IdAndFromStatusAndActionKeyAndEnabledTrue(
            workflowId, "DRAFT", "SUBMIT")).thenReturn(Optional.of(stage));
        when(logs.saveAndFlush(any(WorkflowTransitionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void validatesConfiguredTransitionAndWritesAuditLog() {
        TransitionResponse response = engine.validateTransition(request("APPLICANT", "key-1"));
        assertEquals("VALIDATED", response.decision());
        assertEquals("SUBMITTED", response.toStatus());
        verify(logs).saveAndFlush(any(WorkflowTransitionLog.class));
    }

    @Test
    void rejectsRoleThatDoesNotMatchConfiguredRole() {
        WorkflowException error = assertThrows(WorkflowException.class,
            () -> engine.validateTransition(request("ADMINISTRATOR", "key-1")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDraftWorkflow() {
        WorkflowDefinition workflow = definitions.findById(workflowId).orElseThrow();
        when(workflow.getStatus()).thenReturn(WorkflowStatus.DRAFT);
        WorkflowException error = assertThrows(WorkflowException.class,
            () -> engine.validateTransition(request("APPLICANT", "key-1")));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void identicalRetryReturnsOriginalLogWithoutInsertingAgain() {
        WorkflowTransitionLog previous = new WorkflowTransitionLog();
        previous.setWorkflowDefinitionId(workflowId);
        previous.setWorkflowVersion(2);
        previous.setApplicationId(applicationId);
        previous.setFromStatus("DRAFT");
        previous.setToStatus("SUBMITTED");
        previous.setActionKey("SUBMIT");
        previous.setTriggerType(TriggerType.HUMAN);
        previous.setActorId("applicant-123");
        previous.setActorRole("APPLICANT");
        previous.setComments("");
        when(logs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(previous));

        TransitionResponse response = engine.validateTransition(request("APPLICANT", "key-1"));
        assertEquals("VALIDATED", response.decision());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void rejectsIdempotencyKeyReusedByDifferentActor() {
        WorkflowTransitionLog previous = new WorkflowTransitionLog();
        previous.setWorkflowDefinitionId(workflowId);
        previous.setWorkflowVersion(2);
        previous.setApplicationId(applicationId);
        previous.setFromStatus("DRAFT");
        previous.setToStatus("SUBMITTED");
        previous.setActionKey("SUBMIT");
        previous.setTriggerType(TriggerType.HUMAN);
        previous.setActorId("someone-else");
        previous.setActorRole("APPLICANT");
        previous.setComments("");
        when(logs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(previous));

        WorkflowException error = assertThrows(WorkflowException.class,
            () -> engine.validateTransition(request("APPLICANT", "key-1")));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentTriggerType() {
        WorkflowTransitionLog previous = new WorkflowTransitionLog();
        previous.setWorkflowDefinitionId(workflowId);
        previous.setWorkflowVersion(2);
        previous.setApplicationId(applicationId);
        previous.setFromStatus("DRAFT");
        previous.setToStatus("SUBMITTED");
        previous.setActionKey("SUBMIT");
        previous.setTriggerType(TriggerType.HUMAN);
        previous.setActorId("applicant-123");
        previous.setActorRole("APPLICANT");
        previous.setComments("");
        when(logs.findByIdempotencyKey("key-1")).thenReturn(Optional.of(previous));

        WorkflowException error = assertThrows(WorkflowException.class, () ->
            engine.validateTransition(new ExecuteTransitionRequest(
                workflowId, 2, applicationId, "DRAFT", "SUBMIT", TriggerType.SYSTEM,
                "applicant-123", "APPLICANT", "", "key-1")));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    private ExecuteTransitionRequest request(String role, String key) {
        return new ExecuteTransitionRequest(workflowId, 2, applicationId,
            "DRAFT", "SUBMIT", TriggerType.HUMAN, "applicant-123", role, "", key);
    }
}
