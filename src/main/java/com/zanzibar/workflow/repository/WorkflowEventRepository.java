
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkflowEventRepository
        extends JpaRepository<WorkflowEvent, UUID> {
}
