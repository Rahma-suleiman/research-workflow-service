
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowWebhookRepository
        extends JpaRepository<WorkflowWebhook, UUID> {

    Optional<WorkflowWebhook> findByIdAndTenantKey(UUID id, String tenantKey);

    List<WorkflowWebhook> findByTenantKeyOrderByCreatedAtAsc(String tenantKey);

    List<WorkflowWebhook> findByTenantKeyAndActiveTrue(String tenantKey);
}
