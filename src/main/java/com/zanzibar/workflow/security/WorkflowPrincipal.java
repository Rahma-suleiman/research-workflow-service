package com.zanzibar.workflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * An authenticated caller: either an environment-configured account or a
 * tenant's client app. Every request is scoped to {@link #tenantKey()}.
 */
public final class WorkflowPrincipal implements UserDetails {

    private final String clientId;
    private final String passwordHash;
    private final String tenantKey;
    private final boolean environmentAccount;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public WorkflowPrincipal(
        String clientId,
        String passwordHash,
        String tenantKey,
        boolean environmentAccount,
        boolean enabled,
        List<String> roles
    ) {
        this.clientId = clientId;
        this.passwordHash = passwordHash;
        this.tenantKey = tenantKey;
        this.environmentAccount = environmentAccount;
        this.enabled = enabled;
        this.authorities = roles.stream()
            .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
    }

    public String clientId() {
        return clientId;
    }

    public String tenantKey() {
        return tenantKey;
    }

    /** True for the accounts configured through environment variables. */
    public boolean environmentAccount() {
        return environmentAccount;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return clientId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
