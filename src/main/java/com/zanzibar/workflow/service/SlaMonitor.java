package com.zanzibar.workflow.service;

import com.zanzibar.workflow.enums.WorkflowEventType;
import com.zanzibar.workflow.model.WorkflowInstance;
import com.zanzibar.workflow.repository.WorkflowInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports each SLA breach once: publishes instance.sla_breached for instances
 * past their due date and marks them notified. Entering a new status resets
 * the due date and the notification.
 */
@Service
public class SlaMonitor {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final int batchSize;

    public SlaMonitor(
        WorkflowInstanceRepository instanceRepository,
        WorkflowEventPublisher eventPublisher,
        PlatformTransactionManager transactionManager,
        @Value("${workflow.sla.batch-size:100}") int batchSize
    ) {
        this.instanceRepository = instanceRepository;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
    }

    /** Processes one batch of new breaches and returns how many were reported. */
    public int notifyBreaches() {
        Integer reported = transaction.execute(status -> {
            List<WorkflowInstance> breached = instanceRepository.claimNewSlaBreaches(batchSize);
            for (WorkflowInstance instance : breached) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("instanceId", instance.getId().toString());
                data.put("subjectType", instance.getSubjectType());
                data.put("subjectId", instance.getSubjectId());
                data.put("workflowVersion", instance.getWorkflowVersion());
                data.put("status", instance.getCurrentStatus());
                data.put("statusEnteredAt", instance.getStatusEnteredAt().toString());
                data.put("dueAt", instance.getDueAt().toString());
                eventPublisher.publish(instance.getTenantKey(),
                    WorkflowEventType.INSTANCE_SLA_BREACHED, instance.getId(), data);
                instance.setSlaBreachNotified(true);
                instanceRepository.save(instance);
            }
            return breached.size();
        });
        return reported == null ? 0 : reported;
    }
}
