package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.InstanceActionRequest;
import com.zanzibar.workflow.dto.request.StartInstanceRequest;
import com.zanzibar.workflow.dto.response.InstanceActionResponse;
import com.zanzibar.workflow.dto.response.InstanceResponse;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowInstance;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.model.WorkflowTransitionLog;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.repository.WorkflowInstanceRepository;
import com.zanzibar.workflow.repository.WorkflowStageConfigRepository;
import com.zanzibar.workflow.repository.WorkflowTransitionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowInstanceServiceTest {

    private static final String TENANT = "ACME";

    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final WorkflowDefinitionRepository definitions = mock(WorkflowDefinitionRepository.class);
    private final WorkflowStageConfigRepository stages = mock(WorkflowStageConfigRepository.class);
    private final WorkflowTransitionLogRepository logs = mock(WorkflowTransitionLogRepository.class);
    private final WorkflowResolutionService resolution = mock(WorkflowResolutionService.class);
    private final WorkflowEngineService engine = new WorkflowEngineService(definitions, stages, logs);
    private final WorkflowInstanceService service = new WorkflowInstanceService(
        instances, definitions, stages, logs, resolution, engine);

    private final UUID workflowId = UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e");
    private final UUID instanceId = UUID.fromString("0b7d1f8e-5a3c-4d2e-9f10-1a2b3c4d5e6f");
    private WorkflowDefinition workflow;
    private WorkflowInstance instance;

    @BeforeEach
    void setUp() {
        workflow = mock(WorkflowDefinition.class);
        when(workflow.getId()).thenReturn(workflowId);
        when(workflow.getWorkflowKey()).thenReturn("tickets");
        when(workflow.getWorkflowVersion()).thenReturn(3);
        when(workflow.getStatus()).thenReturn(WorkflowStatus.PUBLISHED);
        when(workflow.isActive()).thenReturn(true);
        when(workflow.getInitialStatus()).thenReturn("NEW");
        when(definitions.findByIdAndTenantKey(workflowId, TENANT)).thenReturn(Optional.of(workflow));

        instance = new WorkflowInstance();
        ReflectionTestUtils.setField(instance, "id", instanceId);
        instance.setTenantKey(TENANT);
        instance.setSubjectType("TICKET");
        instance.setSubjectId("T-1");
        instance.setWorkflowDefinitionId(workflowId);
        instance.setWorkflowVersion(3);
        instance.setCurrentStatus("NEW");
        when(instances.findForUpdate(instanceId, TENANT)).thenReturn(Optional.of(instance));
        when(instances.findByIdAndTenantKey(instanceId, TENANT)).thenReturn(Optional.of(instance));
        when(instances.saveAndFlush(any(WorkflowInstance.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowStageConfig open = stage("NEW", "OPEN", "OPEN_TICKET", "AGENT", 1);
        WorkflowStageConfig cancel = stage("NEW", "CANCELLED", "CANCEL", "CUSTOMER", 2);
        when(stages.findByWorkflowDefinition_IdAndFromStatusAndActionKeyAndEnabledTrue(
            workflowId, "NEW", "OPEN_TICKET")).thenReturn(Optional.of(open));
        when(stages.findByWorkflowDefinition_IdAndFromStatusAndEnabledTrue(workflowId, "NEW"))
            .thenReturn(List.of(open, cancel));
        when(stages.findByWorkflowDefinition_IdAndFromStatusAndEnabledTrue(workflowId, "OPEN"))
            .thenReturn(List.of());

        when(logs.findByTenantKeyAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(logs.saveAndFlush(any(WorkflowTransitionLog.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newInstanceStartsAtTheResolvedWorkflowsInitialStatus() {
        when(instances.findByTenantKeyAndSubjectTypeAndSubjectId(TENANT, "TICKET", "T-2"))
            .thenReturn(Optional.empty());
        when(resolution.resolveDefinitionForNewSubject(TENANT, "TICKET")).thenReturn(workflow);

        WorkflowInstanceService.StartResult result = service.start(TENANT, "acme-backend",
            new StartInstanceRequest("ticket", "T-2"));

        assertTrue(result.created());
        assertEquals("NEW", result.instance().currentStatus());
        assertEquals(3, result.instance().workflowVersion());
        verify(instances).saveAndFlush(argThat(saved -> "acme-backend".equals(saved.getCreatedByClient())));
    }

    @Test
    void startingAnExistingSubjectReturnsItsInstance() {
        when(instances.findByTenantKeyAndSubjectTypeAndSubjectId(TENANT, "TICKET", "T-1"))
            .thenReturn(Optional.of(instance));

        WorkflowInstanceService.StartResult result =
            service.start(TENANT, "acme-backend", new StartInstanceRequest("TICKET", "T-1"));

        assertFalse(result.created());
        assertEquals(instanceId, result.instance().id());
        verify(instances, never()).saveAndFlush(any());
    }

    @Test
    void actionUsesStoredStatusAndMovesTheInstance() {
        InstanceActionResponse response = service.perform(TENANT, "acme-backend", instanceId,
            "open_ticket", action(null, "AGENT", "key-1"));

        assertEquals("NEW", response.fromStatus());
        assertEquals("OPEN", response.toStatus());
        assertFalse(response.replayed());
        assertEquals("OPEN", instance.getCurrentStatus());
        assertTrue(response.instance().terminal());
        verify(logs).saveAndFlush(argThat(log -> instanceId.equals(log.getInstanceId())
            && "NEW".equals(log.getFromStatus()) && "acme-backend".equals(log.getClientId())));
    }

    @Test
    void staleExpectedStatusIsRejectedWithoutChanges() {
        WorkflowException error = assertThrows(WorkflowException.class, () -> service.perform(
            TENANT, "acme-backend", instanceId, "OPEN_TICKET", action("OPEN", "AGENT", "key-1")));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("NEW", instance.getCurrentStatus());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void roleNotAssignedToTheActionCannotMoveTheInstance() {
        WorkflowException error = assertThrows(WorkflowException.class, () -> service.perform(
            TENANT, "acme-backend", instanceId, "OPEN_TICKET", action(null, "CUSTOMER", "key-1")));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("NEW", instance.getCurrentStatus());
    }

    @Test
    void retryWithSameKeyReplaysOriginalResult() {
        WorkflowTransitionLog previous = previousLog("OPEN_TICKET", "AGENT");
        when(logs.findByTenantKeyAndIdempotencyKey(TENANT, "key-1")).thenReturn(Optional.of(previous));
        instance.setCurrentStatus("OPEN");

        InstanceActionResponse response = service.perform(TENANT, "acme-backend", instanceId,
            "OPEN_TICKET", action(null, "AGENT", "key-1"));

        assertTrue(response.replayed());
        assertEquals("OPEN", response.toStatus());
        verify(instances, never()).saveAndFlush(any());
        verify(logs, never()).saveAndFlush(any());
    }

    @Test
    void keyReusedForDifferentActionIsRejected() {
        WorkflowTransitionLog previous = previousLog("CANCEL", "CUSTOMER");
        when(logs.findByTenantKeyAndIdempotencyKey(TENANT, "key-1")).thenReturn(Optional.of(previous));

        WorkflowException error = assertThrows(WorkflowException.class, () -> service.perform(
            TENANT, "acme-backend", instanceId, "OPEN_TICKET", action(null, "AGENT", "key-1")));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
    }

    @Test
    void availableActionsCanBeFilteredByRole() {
        InstanceResponse all = service.get(TENANT, instanceId, null);
        InstanceResponse forCustomer = service.get(TENANT, instanceId, "customer");

        assertEquals(List.of("OPEN_TICKET", "CANCEL"),
            all.availableActions().stream().map(a -> a.actionKey()).toList());
        assertEquals(List.of("CANCEL"),
            forCustomer.availableActions().stream().map(a -> a.actionKey()).toList());
        assertFalse(forCustomer.terminal());
    }

    @Test
    void instanceOfAnotherTenantIsNotFound() {
        when(instances.findForUpdate(instanceId, "OTHER")).thenReturn(Optional.empty());
        WorkflowException error = assertThrows(WorkflowException.class, () -> service.perform(
            "OTHER", "other-backend", instanceId, "OPEN_TICKET", action(null, "AGENT", "key-1")));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    private InstanceActionRequest action(String expectedStatus, String role, String key) {
        return new InstanceActionRequest(expectedStatus, TriggerType.HUMAN, "user-1", role, null, key);
    }

    private WorkflowTransitionLog previousLog(String action, String role) {
        WorkflowTransitionLog log = new WorkflowTransitionLog();
        log.setInstanceId(instanceId);
        log.setActionKey(action);
        log.setFromStatus("NEW");
        log.setToStatus("OPEN");
        log.setTriggerType(TriggerType.HUMAN);
        log.setActorId("user-1");
        log.setActorRole(role);
        return log;
    }

    private WorkflowStageConfig stage(String from, String to, String action, String role, int order) {
        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setName(action);
        stage.setFromStatus(from);
        stage.setToStatus(to);
        stage.setActionKey(action);
        stage.setAssignedRole(role);
        stage.setTriggerType(TriggerType.HUMAN);
        stage.setStageOrder(order);
        stage.setEnabled(true);
        return stage;
    }
}
