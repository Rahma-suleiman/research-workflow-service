package com.zanzibar.workflow.security;

import com.zanzibar.workflow.enums.ClientAppKind;
import com.zanzibar.workflow.model.WorkflowClientApp;
import com.zanzibar.workflow.repository.WorkflowClientAppRepository;
import com.zanzibar.workflow.repository.WorkflowTenantRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Resolves environment-configured accounts first, then tenant client apps
 * stored in the database. A client of an inactive tenant cannot sign in.
 */
public class WorkflowUserDetailsService implements UserDetailsService {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    public static final String WORKFLOW_SERVICE = "WORKFLOW_SERVICE";

    private final Map<String, WorkflowPrincipal> environmentAccounts;
    private final WorkflowClientAppRepository clientRepository;
    private final WorkflowTenantRepository tenantRepository;

    public WorkflowUserDetailsService(
        Map<String, WorkflowPrincipal> environmentAccounts,
        WorkflowClientAppRepository clientRepository,
        WorkflowTenantRepository tenantRepository
    ) {
        this.environmentAccounts = Map.copyOf(environmentAccounts);
        this.clientRepository = clientRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        WorkflowPrincipal environmentAccount = environmentAccounts.get(username);
        if (environmentAccount != null) {
            return environmentAccount;
        }

        WorkflowClientApp client = clientRepository.findByClientId(username)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown client."));
        boolean tenantActive = tenantRepository.findById(client.getTenantKey())
            .map(tenant -> tenant.isActive())
            .orElse(false);

        String role = client.getKind() == ClientAppKind.ADMIN ? ADMINISTRATOR : WORKFLOW_SERVICE;
        return new WorkflowPrincipal(
            client.getClientId(),
            client.getPasswordHash(),
            client.getTenantKey(),
            false,
            client.isActive() && tenantActive,
            List.of(role)
        );
    }
}
