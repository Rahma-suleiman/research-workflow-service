package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.dto.request.InstanceActionRequest;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.exception.WorkflowException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowActorSignatureServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    private final WorkflowActorSignatureService service =
        new WorkflowActorSignatureService(SECRET);

    @Test
    void acceptsSignatureForUnchangedRequest() {
        ExecuteTransitionRequest request = request("APPLICANT", "key-1");
        String signature = service.signRequest(request);
        assertDoesNotThrow(() -> service.verify(request, signature));
    }

    @Test
    void rejectsSignatureWhenActorRoleChanges() {
        ExecuteTransitionRequest signed = request("APPLICANT", "key-1");
        String signature = service.signRequest(signed);
        ExecuteTransitionRequest changed = request("ADMINISTRATOR", "key-1");
        assertThrows(WorkflowException.class, () -> service.verify(changed, signature));
    }

    @Test
    void rejectsSignatureWhenIdempotencyKeyChanges() {
        ExecuteTransitionRequest signed = request("APPLICANT", "key-1");
        String signature = service.signRequest(signed);
        ExecuteTransitionRequest replayWithNewKey = request("APPLICANT", "key-2");
        assertThrows(WorkflowException.class,
            () -> service.verify(replayWithNewKey, signature));
    }

    @Test
    void rejectsSignatureWhenCommentsChange() {
        ExecuteTransitionRequest signed = new ExecuteTransitionRequest(
            UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e"), 2,
            "7f4e6ed8-0ed9-4b20-a2d0-222b40ad6c01",
            "UNDER_REVIEW", "REQUEST_INFORMATION", TriggerType.HUMAN,
            "research-officer-1", "RESEARCH_OFFICER", "Please upload the missing document", "key-1"
        );
        String signature = service.signRequest(signed);
        ExecuteTransitionRequest changed = new ExecuteTransitionRequest(
            signed.workflowDefinitionId(), signed.workflowVersion(), signed.subjectId(),
            signed.currentStatus(), signed.actionKey(), signed.triggerType(), signed.actorId(),
            signed.actorRole(), "Different comments", signed.idempotencyKey()
        );
        assertThrows(WorkflowException.class, () -> service.verify(changed, signature));
    }

    @Test
    void rejectsMissingSignature() {
        assertThrows(WorkflowException.class, () -> service.verify(request("APPLICANT", "key-1"), null));
    }

    @Test
    void refusesToVerifyWhenSecretIsNotConfigured() {
        WorkflowActorSignatureService unconfigured = new WorkflowActorSignatureService("");
        assertThrows(WorkflowException.class,
            () -> unconfigured.verify(request("APPLICANT", "key-1"), "anything"));
    }

    @Test
    void instanceActionSignatureCoversExpectedStatusAndInstance() {
        UUID instanceId = UUID.fromString("0b7d1f8e-5a3c-4d2e-9f10-1a2b3c4d5e6f");
        InstanceActionRequest action = new InstanceActionRequest(
            "NEW", TriggerType.HUMAN, "agent-7", "AGENT", null, "key-1");
        String signature = service.signInstanceAction(instanceId, "OPEN_TICKET", action, SECRET);

        assertDoesNotThrow(() -> service.verifyInstanceAction(
            instanceId, "open_ticket", action, signature, SECRET));
        assertThrows(WorkflowException.class, () -> service.verifyInstanceAction(
            instanceId, "OPEN_TICKET", new InstanceActionRequest(
                "OPEN", TriggerType.HUMAN, "agent-7", "AGENT", null, "key-1"), signature, SECRET));
        assertThrows(WorkflowException.class, () -> service.verifyInstanceAction(
            UUID.randomUUID(), "OPEN_TICKET", action, signature, SECRET));
    }

    @Test
    void validateOnlySignatureCannotBeReusedForInstanceAction() {
        String transitionSignature = service.signRequest(request("AGENT", "key-1"));
        InstanceActionRequest action = new InstanceActionRequest(
            null, TriggerType.HUMAN, "applicant-123", "AGENT", "", "key-1");
        assertThrows(WorkflowException.class, () -> service.verifyInstanceAction(
            UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e"), "SUBMIT", action,
            transitionSignature, SECRET));
    }

    private ExecuteTransitionRequest request(String role, String key) {
        return new ExecuteTransitionRequest(
            UUID.fromString("f3ccca23-b655-4e65-a1ed-ea1d1b8c8a4e"), 2,
            "7f4e6ed8-0ed9-4b20-a2d0-222b40ad6c01",
            "DRAFT", "SUBMIT", TriggerType.HUMAN, "applicant-123", role,
            "", key
        );
    }
}
