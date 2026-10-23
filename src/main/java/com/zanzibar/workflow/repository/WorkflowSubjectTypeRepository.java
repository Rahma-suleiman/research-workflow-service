
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.RegistryKey;
import com.zanzibar.workflow.model.WorkflowSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowSubjectTypeRepository
        extends JpaRepository<WorkflowSubjectType, RegistryKey> {

    List<WorkflowSubjectType> findByTenantKeyOrderByKeyAsc(String tenantKey);

    List<WorkflowSubjectType> findByTenantKeyAndActiveTrueOrderByKeyAsc(String tenantKey);
}
