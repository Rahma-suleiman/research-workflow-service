package com.zanzibar.workflow.config;

import com.zanzibar.workflow.service.SlaMonitor;
import com.zanzibar.workflow.service.WebhookDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Webhook delivery and SLA checks. Safe to run on every node: work is claimed
 * with SKIP LOCKED. Disable with workflow.background-jobs.enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "workflow.background-jobs.enabled", havingValue = "true",
    matchIfMissing = true)
public class BackgroundJobsConfig {

    private static final Logger log = LoggerFactory.getLogger(BackgroundJobsConfig.class);

    private final WebhookDispatcher webhookDispatcher;
    private final SlaMonitor slaMonitor;

    public BackgroundJobsConfig(WebhookDispatcher webhookDispatcher, SlaMonitor slaMonitor) {
        this.webhookDispatcher = webhookDispatcher;
        this.slaMonitor = slaMonitor;
    }

    @Scheduled(fixedDelayString = "${workflow.webhooks.poll-interval-ms:5000}")
    public void dispatchWebhooks() {
        try {
            webhookDispatcher.dispatchDue();
        } catch (RuntimeException exception) {
            log.error("Webhook dispatch failed; it will be retried on the next run.", exception);
        }
    }

    @Scheduled(fixedDelayString = "${workflow.sla.poll-interval-ms:60000}")
    public void checkSlas() {
        try {
            slaMonitor.notifyBreaches();
        } catch (RuntimeException exception) {
            log.error("SLA check failed; it will be retried on the next run.", exception);
        }
    }
}
