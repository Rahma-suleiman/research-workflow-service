
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTenantRepository
        extends JpaRepository<WorkflowTenant, String> {

    List<WorkflowTenant> findAllByOrderByKeyAsc();
}
