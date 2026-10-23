
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowInstance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository
        extends JpaRepository<WorkflowInstance, UUID> {

    Optional<WorkflowInstance> findByIdAndTenantKey(UUID id, String tenantKey);

    Optional<WorkflowInstance> findByTenantKeyAndSubjectTypeAndSubjectId(
        String tenantKey,
        String subjectType,
        String subjectId
    );

    /**
     * Row lock (SELECT ... FOR UPDATE) so concurrent actions on one instance
     * are applied one after another against the latest status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM WorkflowInstance i WHERE i.id = :id AND i.tenantKey = :tenantKey")
    Optional<WorkflowInstance> findForUpdate(
        @Param("id") UUID id,
        @Param("tenantKey") String tenantKey
    );

    List<WorkflowInstance> findTop200ByTenantKeyAndDueAtBeforeOrderByDueAtAsc(
        String tenantKey,
        Instant now
    );

    /** Overdue instances not yet reported; SKIP LOCKED makes this safe on several nodes. */
    @Query(value = """
        SELECT * FROM workflow_instances
        WHERE due_at <= CURRENT_TIMESTAMP AND NOT sla_breach_notified
        ORDER BY due_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<WorkflowInstance> claimNewSlaBreaches(@Param("limit") int limit);
}
