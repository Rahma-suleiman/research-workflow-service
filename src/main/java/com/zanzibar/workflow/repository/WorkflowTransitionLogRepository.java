
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowTransitionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTransitionLogRepository
        extends JpaRepository<WorkflowTransitionLog, UUID> {

    List<WorkflowTransitionLog>
    findByTenantKeyAndSubjectIdOrderByExecutedAtDesc(String tenantKey, String subjectId);

    List<WorkflowTransitionLog> findByInstanceIdOrderByExecutedAtAsc(UUID instanceId);

    Optional<WorkflowTransitionLog> findByTenantKeyAndIdempotencyKey(
        String tenantKey,
        String idempotencyKey
    );
}
