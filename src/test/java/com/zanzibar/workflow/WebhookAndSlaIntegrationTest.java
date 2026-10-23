package com.zanzibar.workflow;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import com.zanzibar.workflow.dto.request.InstanceActionRequest;
import com.zanzibar.workflow.dto.request.StartInstanceRequest;
import com.zanzibar.workflow.enums.TriggerType;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.model.WorkflowStageConfig;
import com.zanzibar.workflow.repository.WorkflowDefinitionRepository;
import com.zanzibar.workflow.service.SlaMonitor;
import com.zanzibar.workflow.service.WebhookDispatcher;
import com.zanzibar.workflow.service.WorkflowInstanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhooks, the transactional outbox and SLA tracking against PostgreSQL and a
 * real local HTTP receiver. Background jobs are disabled in the test profile,
 * so each test drives the dispatcher and SLA monitor directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookAndSlaIntegrationTest {

    @Autowired private WorkflowDefinitionRepository definitions;
    @Autowired private WorkflowInstanceService instanceService;
    @Autowired private WebhookDispatcher dispatcher;
    @Autowired private SlaMonitor slaMonitor;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;

    private final String testPrefix = "wh-" + UUID.randomUUID();
    private final String subjectType =
        "IT_" + UUID.randomUUID().toString().replace("-", "").toUpperCase() + "_WH";

    private record Received(String event, String timestamp, String signature, String body) {}

    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private HttpServer receiver;
    private String receiverUrl;

    @BeforeEach
    void setUp() throws Exception {
        receiver = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        receiver.createContext("/" + testPrefix, exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            received.add(new Received(
                exchange.getRequestHeaders().getFirst(WebhookDispatcher.EVENT_HEADER),
                exchange.getRequestHeaders().getFirst(WebhookDispatcher.TIMESTAMP_HEADER),
                exchange.getRequestHeaders().getFirst(WebhookDispatcher.SIGNATURE_HEADER),
                new String(body, StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        receiver.start();
        receiverUrl = "http://127.0.0.1:" + receiver.getAddress().getPort() + "/" + testPrefix;

        jdbc.update("INSERT INTO workflow_subject_types (tenant_key, type_key, name) VALUES (?, ?, ?)",
            "DEFAULT", subjectType, "Webhook test subject");

        WorkflowDefinition workflow = new WorkflowDefinition();
        workflow.setTenantKey("DEFAULT");
        workflow.setWorkflowKey(testPrefix + "-flow");
        workflow.setName("Webhook test workflow");
        workflow.setScope(WorkflowScope.SELECTED_TYPES);
        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow.setWorkflowVersion(1);
        workflow.setActive(true);
        workflow.setInitialStatus("DRAFT");
        workflow.setSubjectTypes(Set.of(subjectType));
        workflow.addStage(stage("DRAFT", "SUBMITTED", "SUBMIT", "APPLICANT", TriggerType.HUMAN, 1, 2));
        WorkflowStageConfig verify =
            stage("SUBMITTED", "VERIFIED", "VERIFY", "SYSTEM", TriggerType.EXTERNAL_EVENT, 2, null);
        verify.setExternalApproval(true);
        verify.setExternalSystemId("id-verifier");
        workflow.addStage(verify);
        definitions.saveAndFlush(workflow);
    }

    @AfterEach
    void cleanUp() {
        receiver.stop(0);
        String instances = "SELECT id FROM workflow_instances WHERE subject_id LIKE ?";
        jdbc.update("DELETE FROM workflow_webhook_deliveries WHERE event_id IN "
            + "(SELECT id FROM workflow_events WHERE instance_id IN (" + instances + "))", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_webhook_deliveries WHERE webhook_id IN "
            + "(SELECT id FROM workflow_webhooks WHERE url LIKE ?)", "%" + testPrefix + "%");
        jdbc.update("DELETE FROM workflow_events WHERE instance_id IN (" + instances + ")", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_webhooks WHERE url LIKE ?", "%" + testPrefix + "%");
        jdbc.update("DELETE FROM workflow_transition_logs WHERE idempotency_key LIKE ?", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_instances WHERE subject_id LIKE ?", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_definitions WHERE workflow_key LIKE ?", testPrefix + "%");
        jdbc.update("DELETE FROM workflow_subject_types WHERE type_key = ?", subjectType);
    }

    @Test
    void committedChangesAreDeliveredAsSignedWebhooks() throws Exception {
        String secret = registerWebhook("[\"*\"]");
        UUID instanceId = start("-signed");
        perform(instanceId, "SUBMIT", "APPLICANT", "-submit");

        while (dispatcher.dispatchDue() > 0) {
            // drain every due delivery
        }

        assertEquals(List.of("instance.started", "instance.transitioned",
                "instance.external_approval_requested"),
            received.stream().map(Received::event).toList());
        for (Received delivery : received) {
            String expected = "v1=" + WebhookDispatcher.signature(
                secret, Long.parseLong(delivery.timestamp()), delivery.body());
            assertEquals(expected, delivery.signature(), "Every delivery must carry a valid signature");
        }
        Received transitioned = received.get(1);
        assertEquals("SUBMITTED", JsonPath.read(transitioned.body(), "$.data.toStatus"));
        assertEquals(instanceId.toString(), JsonPath.read(transitioned.body(), "$.data.instanceId"));
        assertEquals("id-verifier", JsonPath.read(received.get(2).body(), "$.data.externalSystemId"));
        assertEquals(3, countDeliveries("DELIVERED"));
    }

    @Test
    void rejectedActionPublishesNothing() throws Exception {
        registerWebhook("[\"instance.transitioned\"]");
        UUID instanceId = start("-rejected");

        assertThrows(WorkflowException.class, () -> perform(instanceId, "SUBMIT", "REVIEWER", "-bad"));

        assertEquals(0, countDeliveries(null), "A rolled-back action must not leave an event behind");
        assertEquals(0, dispatcher.dispatchDue());
        assertTrue(received.isEmpty());
    }

    @Test
    void failingEndpointIsRetriedThenFailedAndCanBeRetriedManually() throws Exception {
        registerWebhook("[\"instance.started\"]");
        responseStatus.set(500);
        start("-retry");

        assertEquals(1, dispatcher.dispatchDue());
        assertEquals(1, countDeliveries("PENDING"));
        assertEquals(0, dispatcher.dispatchDue(), "The retry must wait for its backoff");

        jdbc.update("UPDATE workflow_webhook_deliveries SET next_attempt_at = CURRENT_TIMESTAMP "
            + "WHERE webhook_id IN (SELECT id FROM workflow_webhooks WHERE url LIKE ?)", "%" + testPrefix + "%");
        assertEquals(1, dispatcher.dispatchDue());
        assertEquals(1, countDeliveries("FAILED"), "Gives up after max-attempts (2 in tests)");

        String webhookId = jdbc.queryForObject(
            "SELECT id::text FROM workflow_webhooks WHERE url LIKE ?", String.class, "%" + testPrefix + "%");
        String failed = mockMvc.perform(get("/api/webhooks/" + webhookId + "/deliveries")
                .param("status", "FAILED").header("Authorization", adminAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].attempts").value(2))
            .andExpect(jsonPath("$[0].lastStatusCode").value(500))
            .andReturn().getResponse().getContentAsString();
        String deliveryId = JsonPath.read(failed, "$[0].id");

        responseStatus.set(204);
        mockMvc.perform(post("/api/webhooks/deliveries/" + deliveryId + "/retry")
                .header("Authorization", adminAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals(1, dispatcher.dispatchDue());
        assertEquals(1, countDeliveries("DELIVERED"));
        assertEquals(3, received.size());
    }

    @Test
    void slaBreachIsComputedAndReportedOnce() throws Exception {
        registerWebhook("[\"instance.sla_breached\"]");
        UUID instanceId = start("-sla");

        // SUBMIT has slaDays = 2, so the new instance is due two days after it started.
        Long dueInDays = jdbc.queryForObject("SELECT EXTRACT(DAY FROM due_at - status_entered_at)::bigint "
            + "FROM workflow_instances WHERE id = ?", Long.class, instanceId);
        assertEquals(2L, dueInDays);

        jdbc.update("UPDATE workflow_instances SET due_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' "
            + "WHERE id = ?", instanceId);
        mockMvc.perform(get("/api/instances/overdue").header("Authorization", adminAuth()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.instanceId == '" + instanceId + "')]").exists());

        slaMonitor.notifyBreaches();
        slaMonitor.notifyBreaches();
        dispatcher.dispatchDue();

        // Other overdue instances in the shared DEFAULT tenant may be reported too; count only ours.
        List<Received> ours = received.stream()
            .filter(delivery -> instanceId.toString().equals(
                JsonPath.read(delivery.body(), "$.data.instanceId")))
            .toList();
        assertEquals(1, ours.size(), "A breach is reported only once");
        assertEquals("instance.sla_breached", ours.get(0).event());

        // Moving on resets the SLA: SUBMITTED's only action has no slaDays.
        perform(instanceId, "SUBMIT", "APPLICANT", "-move");
        assertNull(jdbc.queryForObject(
            "SELECT due_at FROM workflow_instances WHERE id = ?", java.sql.Timestamp.class, instanceId));
    }

    @Test
    void privateWebhookTargetsAreRejectedByDefaultPolicy() {
        com.zanzibar.workflow.service.WebhookUrlPolicy production =
            new com.zanzibar.workflow.service.WebhookUrlPolicy(true, false);
        assertThrows(WorkflowException.class, () -> production.check(receiverUrl));
    }

    private String registerWebhook(String eventTypes) throws Exception {
        String created = mockMvc.perform(post("/api/webhooks").header("Authorization", adminAuth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"%s\",\"eventTypes\":%s}".formatted(receiverUrl, eventTypes)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.signingSecret").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.signingSecret");
    }

    private UUID start(String suffix) {
        return instanceService.start("DEFAULT", "integration-test",
            new StartInstanceRequest(subjectType, testPrefix + suffix)).instance().id();
    }

    private void perform(UUID instanceId, String action, String role, String keySuffix) {
        instanceService.perform("DEFAULT", "integration-test", instanceId, action,
            new InstanceActionRequest(null, TriggerType.HUMAN, "user-1", role, null, testPrefix + keySuffix));
    }

    private int countDeliveries(String status) {
        String sql = "SELECT COUNT(*) FROM workflow_webhook_deliveries WHERE webhook_id IN "
            + "(SELECT id FROM workflow_webhooks WHERE url LIKE ?)"
            + (status == null ? "" : " AND status = '" + status + "'");
        return jdbc.queryForObject(sql, Integer.class, "%" + testPrefix + "%");
    }

    private WorkflowStageConfig stage(String from, String to, String action, String role,
                                      TriggerType trigger, int order, Integer slaDays) {
        WorkflowStageConfig stage = new WorkflowStageConfig();
        stage.setName(action);
        stage.setFromStatus(from);
        stage.setToStatus(to);
        stage.setActionKey(action);
        stage.setAssignedRole(role);
        stage.setTriggerType(trigger);
        stage.setStageOrder(order);
        stage.setSlaDays(slaDays);
        stage.setEnabled(true);
        return stage;
    }

    private String adminAuth() {
        String token = "workflow_test_admin:test-admin-password-not-for-production";
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
