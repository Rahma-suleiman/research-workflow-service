
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowSubjectTypeRepository
        extends JpaRepository<WorkflowSubjectType, String> {

    List<WorkflowSubjectType> findAllByOrderByKeyAsc();

    List<WorkflowSubjectType> findByActiveTrueOrderByKeyAsc();
}
