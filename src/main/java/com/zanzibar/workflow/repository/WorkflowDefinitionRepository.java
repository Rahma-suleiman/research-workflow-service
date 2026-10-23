
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

public interface WorkflowDefinitionRepository
        extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByWorkflowKeyAndWorkflowVersion(
        String workflowKey,
        Integer workflowVersion
    );

    List<WorkflowDefinition> findAllByWorkflowKeyOrderByWorkflowVersionDesc(
        String workflowKey
    );

    List<WorkflowDefinition> findByStatusAndActiveTrue(
        WorkflowStatus status
    );

    boolean existsByWorkflowKeyAndWorkflowVersion(
        String workflowKey,
        Integer workflowVersion
    );

    @Query("""
        SELECT DISTINCT w
        FROM WorkflowDefinition w
        LEFT JOIN w.subjectTypes subjectType
        WHERE w.status = :status
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
        @Param("status") WorkflowStatus status,
        @Param("allTypes") WorkflowScope allTypes,
        @Param("selectedTypes") WorkflowScope selectedTypes,
        @Param("subjectType") String subjectType
    );
}
