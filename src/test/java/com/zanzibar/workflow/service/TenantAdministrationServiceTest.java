package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateClientAppRequest;
import com.zanzibar.workflow.dto.request.UpdateTenantRequest;
import com.zanzibar.workflow.dto.response.IssuedCredentialsResponse;
import com.zanzibar.workflow.enums.ClientAppKind;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowClientApp;
import com.zanzibar.workflow.model.WorkflowTenant;
import com.zanzibar.workflow.repository.WorkflowClientAppRepository;
import com.zanzibar.workflow.repository.WorkflowTenantRepository;
import com.zanzibar.workflow.security.SecretCipher;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantAdministrationServiceTest {

    private final WorkflowTenantRepository tenants = mock(WorkflowTenantRepository.class);
    private final WorkflowClientAppRepository clients = mock(WorkflowClientAppRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(
        "test-only-encryption-key-32bytes".getBytes(StandardCharsets.UTF_8)));
    private final TenantAdministrationService service = new TenantAdministrationService(
        tenants, clients, encoder, cipher,
        "environment-signing-secret-0123456789", "env_admin", "env_service");

    private final AtomicReference<WorkflowClientApp> saved = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        WorkflowTenant acme = new WorkflowTenant();
        acme.setKey("ACME");
        acme.setName("Acme");
        when(tenants.findById("ACME")).thenReturn(Optional.of(acme));
        when(clients.save(any(WorkflowClientApp.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @Test
    void serviceClientGetsHashedPasswordAndEncryptedSigningSecret() {
        IssuedCredentialsResponse issued = service.createClient("acme",
            new CreateClientAppRequest("acme-backend", ClientAppKind.SERVICE));

        WorkflowClientApp client = saved.get();
        assertEquals("ACME", client.getTenantKey());
        assertTrue(encoder.matches(issued.password(), client.getPasswordHash()));
        assertNotNull(issued.signingSecret());
        assertFalse(client.getSigningSecretEncrypted().contains(issued.signingSecret()));

        when(clients.findByClientId("acme-backend")).thenReturn(Optional.of(client));
        WorkflowPrincipal principal = new WorkflowPrincipal("acme-backend", "", "ACME",
            false, true, List.of("WORKFLOW_SERVICE"));
        assertEquals(issued.signingSecret(), service.signingSecretFor(principal));
    }

    @Test
    void adminClientGetsNoSigningSecret() {
        IssuedCredentialsResponse issued = service.createClient("ACME",
            new CreateClientAppRequest("acme-admin", ClientAppKind.ADMIN));
        assertNull(issued.signingSecret());
        assertNull(saved.get().getSigningSecretEncrypted());
    }

    @Test
    void environmentUsernamesCannotBeReusedAsClientIds() {
        WorkflowException error = assertThrows(WorkflowException.class, () ->
            service.createClient("ACME", new CreateClientAppRequest("env_admin", ClientAppKind.ADMIN)));
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(clients, never()).save(any());
    }

    @Test
    void environmentServiceAccountUsesEnvironmentSigningSecret() {
        WorkflowPrincipal principal = new WorkflowPrincipal("env_service", "", "DEFAULT",
            true, true, List.of("WORKFLOW_SERVICE"));
        assertEquals("environment-signing-secret-0123456789", service.signingSecretFor(principal));
    }

    @Test
    void defaultTenantCannotBeDeactivated() {
        WorkflowTenant defaultTenant = new WorkflowTenant();
        defaultTenant.setKey("DEFAULT");
        when(tenants.findById("DEFAULT")).thenReturn(Optional.of(defaultTenant));
        assertThrows(WorkflowException.class,
            () -> service.updateTenant("DEFAULT", new UpdateTenantRequest("Default", false)));
    }
}
