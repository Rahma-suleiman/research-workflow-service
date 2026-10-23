
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRoleRepository
        extends JpaRepository<WorkflowRole, String> {

    List<WorkflowRole> findAllByOrderByKeyAsc();

    List<WorkflowRole> findByActiveTrueOrderByKeyAsc();
}
