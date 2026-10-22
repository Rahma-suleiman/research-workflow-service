
package com.zanzibar.workflow.config;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

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

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @PostConstruct
    void validateProductionSecrets() {
        boolean production = java.util.Arrays.stream(activeProfiles.split(","))
            .map(String::trim)
            .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                || profile.equalsIgnoreCase("production"));
        if (!production) {
            return;
        }

        if (isWeak(adminPassword) || isWeak(servicePassword)
                || isWeak(databasePassword) || isWeak(actorSigningSecret)
                || actorSigningSecret.length() < 32) {
            throw new IllegalStateException(
                "Production requires non-default database, administrator, "
                    + "service, and actor-signing secrets. Configure them through "
                    + "a secret manager or protected environment variables."
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

    @Bean
    public UserDetailsService userDetailsService(
        PasswordEncoder passwordEncoder
    ) {
        var admin = User.withUsername(adminUsername)
            .password(passwordEncoder.encode(adminPassword))
            .roles("ADMINISTRATOR")
            .build();

        var workflowService = User.withUsername(serviceUsername)
            .password(passwordEncoder.encode(servicePassword))
            .roles("WORKFLOW_SERVICE")
            .build();

        return new InMemoryUserDetailsManager(admin, workflowService);
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
