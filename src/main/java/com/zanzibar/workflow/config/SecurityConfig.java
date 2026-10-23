
package com.zanzibar.workflow.config;

import com.zanzibar.workflow.model.WorkflowTenant;
import com.zanzibar.workflow.repository.WorkflowClientAppRepository;
import com.zanzibar.workflow.repository.WorkflowTenantRepository;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import com.zanzibar.workflow.security.WorkflowUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${workflow.security.admin-username}")
    private String adminUsername;

    @Value("${workflow.security.admin-password}")
    private String adminPassword;

    @Value("${workflow.security.service-username}")
    private String serviceUsername;

    @Value("${workflow.security.service-password}")
    private String servicePassword;

    @Value("${workflow.security.actor-signing-secret:}")
    private String actorSigningSecret;

    @Value("${workflow.security.secret-encryption-key:}")
    private String secretEncryptionKey;

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionSecrets() {
        boolean production = java.util.Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                || profile.equalsIgnoreCase("production"));
        if (!production) {
            return;
        }

        if (isWeak(adminPassword) || isWeak(servicePassword)
                || isWeak(databasePassword) || isWeak(actorSigningSecret)
                || actorSigningSecret.length() < 32
                || secretEncryptionKey == null || secretEncryptionKey.isBlank()) {
            throw new IllegalStateException(
                "Production requires non-default database, administrator, "
                    + "service, actor-signing, and secret-encryption secrets. Configure "
                    + "them through a secret manager or protected environment variables."
            );
        }
    }

    private boolean isWeak(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("12345") || normalized.equals("admin123")
            || normalized.equals("workflow123") || normalized.length() < 16;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The environment administrator is the platform administrator (manages
     * tenants and client apps) and also administers the DEFAULT tenant. The
     * environment service account is the DEFAULT tenant's backend. Every other
     * caller is a tenant client app stored in the database.
     */
    @Bean
    public UserDetailsService userDetailsService(
        PasswordEncoder passwordEncoder,
        WorkflowClientAppRepository clientRepository,
        WorkflowTenantRepository tenantRepository
    ) {
        if (adminUsername.equals(serviceUsername)) {
            throw new IllegalStateException(
                "Administrator and service usernames must be different.");
        }
        var admin = new WorkflowPrincipal(adminUsername,
            passwordEncoder.encode(adminPassword), WorkflowTenant.DEFAULT_TENANT, true, true,
            List.of(WorkflowUserDetailsService.PLATFORM_ADMIN,
                WorkflowUserDetailsService.ADMINISTRATOR));
        var workflowService = new WorkflowPrincipal(serviceUsername,
            passwordEncoder.encode(servicePassword), WorkflowTenant.DEFAULT_TENANT, true, true,
            List.of(WorkflowUserDetailsService.WORKFLOW_SERVICE));

        return new WorkflowUserDetailsService(
            Map.of(adminUsername, admin, serviceUsername, workflowService),
            clientRepository, tenantRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/health/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .cacheControl(Customizer.withDefaults())
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
