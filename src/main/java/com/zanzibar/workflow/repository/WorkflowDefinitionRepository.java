
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowDefinition;
import com.zanzibar.workflow.enums.WorkflowScope;
import com.zanzibar.workflow.enums.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every lookup is scoped to one tenant so projects never see each other's data. */
public interface WorkflowDefinitionRepository
        extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByIdAndTenantKey(UUID id, String tenantKey);

    List<WorkflowDefinition> findByTenantKeyOrderByWorkflowKeyAscWorkflowVersionDesc(
        String tenantKey
    );

    List<WorkflowDefinition> findAllByTenantKeyAndWorkflowKeyOrderByWorkflowVersionDesc(
        String tenantKey,
        String workflowKey
    );

    List<WorkflowDefinition> findByTenantKeyAndStatusAndActiveTrue(
        String tenantKey,
        WorkflowStatus status
    );

    @Query("""
        SELECT DISTINCT w
        FROM WorkflowDefinition w
        LEFT JOIN w.subjectTypes subjectType
        WHERE w.tenantKey = :tenantKey
          AND w.status = :status
          AND w.active = true
          AND (
              w.scope = :allTypes
              OR (
                  w.scope = :selectedTypes
                  AND subjectType = :subjectType
              )
          )
        """)
    List<WorkflowDefinition> findActivePublishedForSubjectType(
        @Param("tenantKey") String tenantKey,
        @Param("status") WorkflowStatus status,
        @Param("allTypes") WorkflowScope allTypes,
        @Param("selectedTypes") WorkflowScope selectedTypes,
        @Param("subjectType") String subjectType
    );
}
