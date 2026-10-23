package com.zanzibar.workflow;

import com.jayway.jsonpath.JsonPath;
import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.service.WorkflowEngineService;
import com.zanzibar.workflow.service.WorkflowActorSignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.springframework.http.MediaType;

/**
 * Integration tests that use a real PostgreSQL database. The test profile is
 * pinned to workflow_engine_test_db; it never uses workflow_engine_db.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostgresConcurrencyAndSecurityIntegrationTest {

    @Autowired private WorkflowDefinitionRepository definitions;
    @Autowired private WorkflowEngineService engine;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MockMvc mockMvc;
    @Autowired private WorkflowActorSignatureService actorSignatureService;

    private final String testPrefix = "it-" + UUID.randomUUID();
    private final String registryPrefix =
        "IT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase() + "_";
    private final String subjectType = registryPrefix + "TYPE";
    private UUID workflowId;
    private String idempotencyKey;

    @BeforeEach
    void createIsolatedPublishedWorkflow() {
        idempotencyKey = testPrefix + "-same-key";
        jdbc.update("INSERT INTO workflow_subject_types (type_key, name) VALUES (?, ?)",
            subjectType, "Integration test subject type");
        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setWorkflowKey("it-workflow-" + UUID.randomUUID());
        workflow.setName("PostgreSQL integration test workflow");
        workflow.setDescription("Created automatically by integration tests");
        workflow.setScope(WorkflowScope.SELECTED_TYPES);
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow.setWorkflowVersion(1);
        workflow.setActive(true);
        workflow.setInitialStatus("DRAFT");
        workflow.setSubjectTypes(java.util.Set.of(subjectType));

        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setName("Submit application");
        stage.setFromStatus("DRAFT");
        stage.setToStatus("SUBMITTED");
        stage.setActionKey("SUBMIT");
        stage.setAssignedRole("APPLICANT");
        stage.setTriggerType(TriggerType.HUMAN);
        stage.setStageOrder(1);
        stage.setRequiresComments(false);
        stage.setExternalApproval(false);
        stage.setEnabled(true);
        workflow.addStage(stage);

        workflowId = definitions.saveAndFlush(workflow).getId();
    }

    @AfterEach
    void cleanOnlyRowsCreatedByThisTest() {
        jdbc.update("DELETE FROM workflow_transition_logs WHERE idempotency_key LIKE ?", testPrefix + "%");
        if (workflowId != null) {
            definitions.deleteById(workflowId);
            definitions.flush();
        }
        jdbc.update("DELETE FROM workflow_definitions WHERE workflow_key LIKE ?", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_subject_types WHERE type_key LIKE ?", registryPrefix + "%");
        jdbc.update("DELETE FROM workflow_roles WHERE role_key LIKE ?", registryPrefix + "%");
    }

    @Test
    void simultaneousSameIdempotencyKeyCreatesAtMostOneAuditRow() throws Exception {
        String subjectId = UUID.randomUUID().toString();
        ExecuteTransitionRequest request = new ExecuteTransitionRequest(
            workflowId, 1, subjectId, "DRAFT", "SUBMIT", TriggerType.HUMAN,
            "test-applicant", "APPLICANT", "", idempotencyKey
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> callTransition(request, ready, start));
            Future<Boolean> second = pool.submit(() -> callTransition(request, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both requests should reach the start gate");
            start.countDown();
            boolean firstCompleted = first.get(20, TimeUnit.SECONDS);
            boolean secondCompleted = second.get(20, TimeUnit.SECONDS);
            assertTrue(firstCompleted || secondCompleted,
                "At least one identical request should be accepted or recognized as a retry");
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workflow_transition_logs WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
            assertEquals(1, count, "A unique idempotency key must produce exactly one audit row");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentWorkflowEditsDoNotSilentlyOverwriteEachOther() throws Exception {
        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch allowUpdates = new CountDownLatch(1);
        AtomicInteger successfulCommits = new AtomicInteger();
        AtomicInteger rejectedCommits = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            Future<?> first = pool.submit(() -> updateWorkflowName(tx, bothLoaded, allowUpdates,
                "concurrent-edit-A", successfulCommits, rejectedCommits));
            Future<?> second = pool.submit(() -> updateWorkflowName(tx, bothLoaded, allowUpdates,
                "concurrent-edit-B", successfulCommits, rejectedCommits));
            assertTrue(bothLoaded.await(10, TimeUnit.SECONDS), "Both transactions must load the same version");
            allowUpdates.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            assertEquals(1, successfulCommits.get(), "Exactly one edit should commit");
            assertEquals(1, rejectedCommits.get(), "The stale edit should be rejected by optimistic locking");
        } finally {
            allowUpdates.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void workflowAdministrationRequiresAuthenticationAndAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/workflows"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workflows").header("Authorization", basic("workflow_test_service",
                "test-service-password-not-for-production")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workflows").header("Authorization", basic("workflow_test_admin",
                "test-admin-password-not-for-production")))
            .andExpect(status().isOk());
    }


    @Test
    void transitionEndpointRejectsMissingActorSignature() throws Exception {
        ExecuteTransitionRequest request = httpTransitionRequest(
            testPrefix + "-missing-signature"
        );

        mockMvc.perform(post("/api/transitions/execute")
                .header("Authorization", basic("workflow_test_service",
                    "test-service-password-not-for-production"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void transitionEndpointRejectsInvalidActorSignature() throws Exception {
        ExecuteTransitionRequest request = httpTransitionRequest(
            testPrefix + "-invalid-signature"
        );

        mockMvc.perform(post("/api/transitions/execute")
                .header("Authorization", basic("workflow_test_service",
                    "test-service-password-not-for-production"))
                .header("X-Actor-Signature", "not-a-valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(request)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void transitionEndpointAcceptsValidActorSignature() throws Exception {
        ExecuteTransitionRequest request = httpTransitionRequest(
            testPrefix + "-valid-signature"
        );
        String signature = actorSignatureService.signRequest(request);

        mockMvc.perform(post("/api/transitions/execute")
                .header("Authorization", basic("workflow_test_service",
                    "test-service-password-not-for-production"))
                .header("X-Actor-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(request)))
            .andExpect(status().isOk());
    }

    @Test
    void registryEndpointsRequireAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/roles").header("Authorization", serviceAuth()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/subject-types").header("Authorization", adminAuth()))
            .andExpect(status().isOk());
    }

    /**
     * A project unrelated to research permits configures its own vocabulary and
     * workflow entirely over HTTP and runs a transition on a non-UUID subject.
     */
    @Test
    void anyProjectCanConfigureAndUseItsOwnWorkflowOverHttp() throws Exception {
        String role = registryPrefix + "AGENT";
        String type = registryPrefix + "TICKET";

        mockMvc.perform(post("/api/roles").header("Authorization", adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"%s\",\"name\":\"Support agent\"}".formatted(role)))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/subject-types").header("Authorization", adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"%s\",\"name\":\"Support ticket\"}".formatted(type)))
            .andExpect(status().isCreated());

        String created = mockMvc.perform(post("/api/workflows").header("Authorization", adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"workflowKey":"%s-tickets","name":"Ticket flow","initialStatus":"NEW",
                     "scope":"SELECTED_TYPES","subjectTypes":["%s"]}
                    """.formatted(testPrefix, type)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.initialStatus").value("NEW"))
            .andReturn().getResponse().getContentAsString();
        String ticketWorkflowId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/workflows/" + ticketWorkflowId + "/stages")
                .header("Authorization", adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Open ticket","fromStatus":"NEW","toStatus":"OPEN",
                     "actionKey":"OPEN_TICKET","assignedRole":"%s","triggerType":"HUMAN",
                     "stageOrder":1,"requiresComments":false,"externalApproval":false,"enabled":true}
                    """.formatted(role)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/workflows/" + ticketWorkflowId + "/publish")
                .header("Authorization", adminAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/workflows/resolve").param("subjectType", type)
                .header("Authorization", serviceAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(ticketWorkflowId));

        ExecuteTransitionRequest request = new ExecuteTransitionRequest(
            UUID.fromString(ticketWorkflowId), 1, "TICKET-2026-0042", "NEW", "OPEN_TICKET",
            TriggerType.HUMAN, "agent-7", role, "", testPrefix + "-ticket");
        mockMvc.perform(post("/api/transitions/execute")
                .header("Authorization", serviceAuth())
                .header("X-Actor-Signature", actorSignatureService.signRequest(request))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subjectId").value("TICKET-2026-0042"))
            .andExpect(jsonPath("$.toStatus").value("OPEN"));
    }

    private String adminAuth() {
        return basic("workflow_test_admin", "test-admin-password-not-for-production");
    }

    private String serviceAuth() {
        return basic("workflow_test_service", "test-service-password-not-for-production");
    }

    private String transitionJson(ExecuteTransitionRequest request) {
        return """
            {
              "workflowDefinitionId": "%s",
              "workflowVersion": %d,
              "subjectId": "%s",
              "currentStatus": "%s",
              "actionKey": "%s",
              "triggerType": "%s",
              "actorId": "%s",
              "actorRole": "%s",
              "comments": "%s",
              "idempotencyKey": "%s"
            }
            """.formatted(
                request.workflowDefinitionId(),
                request.workflowVersion(),
                request.subjectId(),
                request.currentStatus(),
                request.actionKey(),
                request.triggerType().name(),
                request.actorId(),
                request.actorRole(),
                request.comments(),
                request.idempotencyKey()
            );
    }

    private ExecuteTransitionRequest httpTransitionRequest(String key) {
        return new ExecuteTransitionRequest(
            workflowId,
            1,
            UUID.randomUUID().toString(),
            "DRAFT",
            "SUBMIT",
            TriggerType.HUMAN,
            "test-applicant",
            "APPLICANT",
            "",
            key
        );
    }

    private boolean callTransition(ExecuteTransitionRequest request,
                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            engine.validateTransition(request);
            return true;
        } catch (WorkflowException expectedConflictOrValidationFailure) {
            // A concurrent insert may legitimately lose the unique-key race and return HTTP 409.
            if (expectedConflictOrValidationFailure.getStatus().value() == 409) return false;
            throw expectedConflictOrValidationFailure;
        }
    }

    private void updateWorkflowName(TransactionTemplate tx, CountDownLatch bothLoaded,
                                    CountDownLatch allowUpdates, String name,
                                    AtomicInteger successful, AtomicInteger rejected) {
        try {
            tx.executeWithoutResult(status -> {
                WorkflowDefinition workflow = definitions.findById(workflowId).orElseThrow();
                bothLoaded.countDown();
                await(allowUpdates);
                workflow.setName(name);
                definitions.saveAndFlush(workflow);
            });
            successful.incrementAndGet();
        } catch (RuntimeException exception) {
            if (hasOptimisticLockCause(exception)) {
                rejected.incrementAndGet();
            } else {
                throw exception;
            }
        }
    }

    private boolean hasOptimisticLockCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test gate");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted", exception);
        }
    }

    private String basic(String username, String password) {
        String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
