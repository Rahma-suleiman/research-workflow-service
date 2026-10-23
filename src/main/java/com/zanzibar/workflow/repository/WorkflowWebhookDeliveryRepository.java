
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.enums.DeliveryStatus;
import com.zanzibar.workflow.model.WorkflowWebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkflowWebhookDeliveryRepository
        extends JpaRepository<WorkflowWebhookDelivery, UUID> {

    /**
     * Claims due deliveries. SKIP LOCKED lets several engine nodes dispatch
     * at once without ever picking up the same delivery.
     */
    @Query(value = """
        SELECT * FROM workflow_webhook_deliveries
        WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
        ORDER BY next_attempt_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<WorkflowWebhookDelivery> claimDue(@Param("limit") int limit);

    List<WorkflowWebhookDelivery> findTop100ByWebhookIdOrderByCreatedAtDesc(UUID webhookId);

    List<WorkflowWebhookDelivery> findTop100ByWebhookIdAndStatusOrderByCreatedAtDesc(
        UUID webhookId,
        DeliveryStatus status
    );
}
