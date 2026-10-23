
package com.zanzibar.workflow.repository;

import com.zanzibar.workflow.model.WorkflowClientApp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowClientAppRepository
        extends JpaRepository<WorkflowClientApp, UUID> {

    Optional<WorkflowClientApp> findByClientId(String clientId);

    boolean existsByClientId(String clientId);

    List<WorkflowClientApp> findByTenantKeyOrderByClientIdAsc(String tenantKey);
}
