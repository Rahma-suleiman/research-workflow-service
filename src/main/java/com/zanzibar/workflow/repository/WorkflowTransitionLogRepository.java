
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowTransitionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTransitionLogRepository
        extends JpaRepository<WorkflowTransitionLog, UUID> {

    List<WorkflowTransitionLog>
    findBySubjectIdOrderByExecutedAtDesc(String subjectId);

    List<WorkflowTransitionLog>
    findByWorkflowDefinitionIdAndWorkflowVersionOrderByExecutedAtDesc(
        UUID workflowDefinitionId,
        Integer workflowVersion
    );

    Optional<WorkflowTransitionLog> findByIdempotencyKey(
        String idempotencyKey
    );

    boolean existsByIdempotencyKey(String idempotencyKey);
}
