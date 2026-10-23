package com.zanzibar.workflow.service;

import com.zanzibar.workflow.enums.WorkflowEventType;
import com.zanzibar.workflow.model.WorkflowWebhook;
import com.zanzibar.workflow.repository.WorkflowEventRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookDeliveryRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookRepository;
import com.zanzibar.workflow.security.SecretCipher;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebhookDispatcherTest {

    private final WebhookDispatcher dispatcher = new WebhookDispatcher(
        mock(WorkflowWebhookDeliveryRepository.class), mock(WorkflowWebhookRepository.class),
        mock(WorkflowEventRepository.class), new SecretCipher(""),
        new WebhookUrlPolicy(false, false), mock(PlatformTransactionManager.class),
        50, 8, 120, 10, 30, 21600);

    @Test
    void backoffDoublesAndIsCapped() {
        assertEquals(Duration.ofSeconds(30), dispatcher.backoff(1));
        assertEquals(Duration.ofSeconds(60), dispatcher.backoff(2));
        assertEquals(Duration.ofSeconds(120), dispatcher.backoff(3));
        assertEquals(Duration.ofHours(6), dispatcher.backoff(20));
        assertEquals(Duration.ofHours(6), dispatcher.backoff(1000));
    }

    @Test
    void signatureCoversTimestampAndBody() {
        String signature = WebhookDispatcher.signature("secret", 1700000000L, "{\"a\":1}");
        assertEquals(64, signature.length());
        assertNotEquals(signature, WebhookDispatcher.signature("secret", 1700000001L, "{\"a\":1}"));
        assertNotEquals(signature, WebhookDispatcher.signature("secret", 1700000000L, "{\"a\":2}"));
        assertNotEquals(signature, WebhookDispatcher.signature("other", 1700000000L, "{\"a\":1}"));
    }

    @Test
    void subscriptionMatchesWildcardOrExactEventType() {
        WorkflowWebhook all = new WorkflowWebhook();
        all.setEventTypes("*");
        WorkflowWebhook some = new WorkflowWebhook();
        some.setEventTypes("instance.started,instance.sla_breached");

        assertTrue(WorkflowEventPublisher.subscribes(all, WorkflowEventType.INSTANCE_TRANSITIONED));
        assertTrue(WorkflowEventPublisher.subscribes(some, WorkflowEventType.INSTANCE_SLA_BREACHED));
        assertFalse(WorkflowEventPublisher.subscribes(some, WorkflowEventType.INSTANCE_TRANSITIONED));
    }
}
