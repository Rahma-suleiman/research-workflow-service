
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
        LEFT JOIN w.applicableApplicationTypes applicationType
        WHERE w.status = :status
          AND w.active = true
          AND (
              w.scope = :allApplications
              OR (
                  w.scope = :selectedTypes
                  AND applicationType = :applicationType
              )
          )
        """)
    List<WorkflowDefinition> findActivePublishedForApplicationType(
        @Param("status") WorkflowStatus status,
        @Param("allApplications") WorkflowScope allApplications,
        @Param("selectedTypes") WorkflowScope selectedTypes,
        @Param("applicationType") String applicationType
    );
}
