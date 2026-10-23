package com.zanzibar.workflow.service;

import com.zanzibar.workflow.enums.DeliveryStatus;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowEvent;
import com.zanzibar.workflow.model.WorkflowWebhook;
import com.zanzibar.workflow.model.WorkflowWebhookDelivery;
import com.zanzibar.workflow.repository.WorkflowEventRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookDeliveryRepository;
import com.zanzibar.workflow.repository.WorkflowWebhookRepository;
import com.zanzibar.workflow.security.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Delivers pending webhook deliveries with at-least-once semantics.
 *
 * <p>Deliveries are claimed in a short transaction that pushes their next
 * attempt out by a lease, then sent outside any transaction so a slow endpoint
 * never holds database locks. If a node dies mid-send, the lease expires and
 * another node retries. Receivers should de-duplicate by the event ID.
 */
@Service
public class WebhookDispatcher {

    public static final String SIGNATURE_HEADER = "X-Workflow-Signature";
    public static final String TIMESTAMP_HEADER = "X-Workflow-Timestamp";
    public static final String EVENT_HEADER = "X-Workflow-Event";
    public static final String DELIVERY_HEADER = "X-Workflow-Delivery";

    private static final int MAX_ERROR_LENGTH = 1000;

    private final WorkflowWebhookDeliveryRepository deliveryRepository;
    private final WorkflowWebhookRepository webhookRepository;
    private final WorkflowEventRepository eventRepository;
    private final SecretCipher secretCipher;
    private final WebhookUrlPolicy urlPolicy;
    private final TransactionTemplate transaction;
    private final HttpClient httpClient;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration lease;
    private final Duration requestTimeout;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public WebhookDispatcher(
        WorkflowWebhookDeliveryRepository deliveryRepository,
        WorkflowWebhookRepository webhookRepository,
        WorkflowEventRepository eventRepository,
        SecretCipher secretCipher,
        WebhookUrlPolicy urlPolicy,
        PlatformTransactionManager transactionManager,
        @Value("${workflow.webhooks.batch-size:50}") int batchSize,
        @Value("${workflow.webhooks.max-attempts:8}") int maxAttempts,
        @Value("${workflow.webhooks.lease-seconds:120}") long leaseSeconds,
        @Value("${workflow.webhooks.timeout-seconds:10}") long timeoutSeconds,
        @Value("${workflow.webhooks.base-backoff-seconds:30}") long baseBackoffSeconds,
        @Value("${workflow.webhooks.max-backoff-seconds:21600}") long maxBackoffSeconds
    ) {
        this.deliveryRepository = deliveryRepository;
        this.webhookRepository = webhookRepository;
        this.eventRepository = eventRepository;
        this.secretCipher = secretCipher;
        this.urlPolicy = urlPolicy;
        this.transaction = new TransactionTemplate(transactionManager);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.lease = Duration.ofSeconds(leaseSeconds);
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.baseBackoff = Duration.ofSeconds(baseBackoffSeconds);
        this.maxBackoff = Duration.ofSeconds(maxBackoffSeconds);
    }

    /** Sends one batch of due deliveries and returns how many were attempted. */
    public int dispatchDue() {
        List<Attempt> attempts = transaction.execute(status -> claim());
        if (attempts == null) {
            return 0;
        }
        for (Attempt attempt : attempts) {
            Outcome outcome = send(attempt);
            transaction.executeWithoutResult(status -> record(attempt.deliveryId(), outcome));
        }
        return attempts.size();
    }

    /** HMAC-SHA256 over "timestamp.body", hex encoded, as sent in X-Workflow-Signature (v1=...). */
    public static String signature(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign webhook payload.", exception);
        }
    }

    private record Attempt(
        UUID deliveryId,
        String url,
        String secret,
        String eventType,
        String payload,
        String permanentFailure
    ) {}

    private record Outcome(boolean delivered, Integer statusCode, String error, boolean permanent) {}

    private List<Attempt> claim() {
        Instant now = Instant.now();
        List<Attempt> attempts = new ArrayList<>();
        for (WorkflowWebhookDelivery delivery : deliveryRepository.claimDue(batchSize)) {
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setNextAttemptAt(now.plus(lease));
            deliveryRepository.save(delivery);

            WorkflowWebhook webhook = webhookRepository.findById(delivery.getWebhookId()).orElse(null);
            WorkflowEvent event = eventRepository.findById(delivery.getEventId()).orElse(null);
            if (webhook == null || !webhook.isActive() || event == null) {
                attempts.add(new Attempt(delivery.getId(), null, null, null, null,
                    "The webhook is inactive or was removed."));
                continue;
            }
            try {
                String secret = secretCipher.decrypt(
                    webhook.getSecretEncrypted(), webhook.getId().toString());
                attempts.add(new Attempt(delivery.getId(), webhook.getUrl(), secret,
                    event.getEventType(), event.getPayload(), null));
            } catch (RuntimeException exception) {
                // A missing or changed encryption key will not fix itself on retry.
                attempts.add(new Attempt(delivery.getId(), null, null, null, null,
                    "The webhook signing secret cannot be decrypted."));
            }
        }
        return attempts;
    }

    private Outcome send(Attempt attempt) {
        if (attempt.permanentFailure() != null) {
            return new Outcome(false, null, attempt.permanentFailure(), true);
        }
        URI uri;
        try {
            uri = urlPolicy.check(attempt.url());
        } catch (WorkflowException rejected) {
            return new Outcome(false, null, rejected.getMessage(), false);
        }

        long timestamp = Instant.now().getEpochSecond();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .header("User-Agent", "workflow-engine-webhooks/1")
            .header(EVENT_HEADER, attempt.eventType())
            .header(DELIVERY_HEADER, attempt.deliveryId().toString())
            .header(TIMESTAMP_HEADER, Long.toString(timestamp))
            .header(SIGNATURE_HEADER, "v1=" + signature(attempt.secret(), timestamp, attempt.payload()))
            .POST(HttpRequest.BodyPublishers.ofString(attempt.payload(), StandardCharsets.UTF_8))
            .build();
        try {
            int status = httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300
                ? new Outcome(true, status, null, false)
                : new Outcome(false, status, "Endpoint responded with HTTP " + status + ".", false);
        } catch (IOException exception) {
            return new Outcome(false, null,
                exception.getClass().getSimpleName() + ": " + exception.getMessage(), false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Outcome(false, null, "Delivery was interrupted.", false);
        }
    }

    private void record(UUID deliveryId, Outcome outcome) {
        WorkflowWebhookDelivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        delivery.setLastStatusCode(outcome.statusCode());
        if (outcome.delivered()) {
            delivery.setStatus(DeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(Instant.now());
            delivery.setLastError(null);
        } else {
            delivery.setLastError(truncate(outcome.error()));
            if (outcome.permanent() || delivery.getAttempts() >= maxAttempts) {
                delivery.setStatus(DeliveryStatus.FAILED);
            } else {
                delivery.setStatus(DeliveryStatus.PENDING);
                delivery.setNextAttemptAt(Instant.now().plus(backoff(delivery.getAttempts())));
            }
        }
        deliveryRepository.save(delivery);
    }

    /** 30s, 1m, 2m, 4m, ... capped at the configured maximum (default 6h). */
    Duration backoff(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 30);
        Duration delay = baseBackoff.multipliedBy(1L << exponent);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
