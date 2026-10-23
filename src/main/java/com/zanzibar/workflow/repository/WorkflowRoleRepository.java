
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.RegistryKey;
import com.zanzibar.workflow.model.WorkflowRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRoleRepository
        extends JpaRepository<WorkflowRole, RegistryKey> {

    List<WorkflowRole> findByTenantKeyOrderByKeyAsc(String tenantKey);

    List<WorkflowRole> findByTenantKeyAndActiveTrueOrderByKeyAsc(String tenantKey);
}
