
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowStageConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowStageConfigRepository
        extends JpaRepository<WorkflowStageConfig, UUID> {

    List<WorkflowStageConfig>
    findByWorkflowDefinition_IdOrderByStageOrderAsc(UUID workflowDefinitionId);

    List<WorkflowStageConfig>
    findByWorkflowDefinition_IdAndFromStatusAndEnabledTrue(
        UUID workflowDefinitionId,
        String fromStatus
    );

    Optional<WorkflowStageConfig>
    findByWorkflowDefinition_IdAndFromStatusAndActionKeyAndEnabledTrue(
        UUID workflowDefinitionId,
        String fromStatus,
        String actionKey
    );

    boolean existsByWorkflowDefinition_IdAndFromStatusAndActionKey(
        UUID workflowDefinitionId,
        String fromStatus,
        String actionKey
    );

    long countByWorkflowDefinition_Id(UUID workflowDefinitionId);

    void deleteByWorkflowDefinition_Id(UUID workflowDefinitionId);
}
