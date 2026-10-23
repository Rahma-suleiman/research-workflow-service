package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.CreateClientAppRequest;
import com.zanzibar.workflow.dto.request.CreateTenantRequest;
import com.zanzibar.workflow.dto.request.UpdateClientAppRequest;
import com.zanzibar.workflow.dto.request.UpdateTenantRequest;
import com.zanzibar.workflow.dto.response.ClientAppResponse;
import com.zanzibar.workflow.dto.response.IssuedCredentialsResponse;
import com.zanzibar.workflow.dto.response.TenantResponse;
import com.zanzibar.workflow.enums.ClientAppKind;
import com.zanzibar.workflow.exception.WorkflowException;
import com.zanzibar.workflow.model.WorkflowClientApp;
import com.zanzibar.workflow.model.WorkflowTenant;
import com.zanzibar.workflow.repository.WorkflowClientAppRepository;
import com.zanzibar.workflow.repository.WorkflowTenantRepository;
import com.zanzibar.workflow.security.SecretCipher;
import com.zanzibar.workflow.security.SecretGenerator;
import com.zanzibar.workflow.security.WorkflowPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Platform administration: tenants (one per project) and the client apps
 * each tenant uses to call the engine.
 */
@Service
public class TenantAdministrationService {

    private static final Pattern TENANT_KEY_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private final WorkflowTenantRepository tenantRepository;
    private final WorkflowClientAppRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretCipher secretCipher;
    private final String environmentSigningSecret;
    private final Set<String> reservedClientIds;

    public TenantAdministrationService(
        WorkflowTenantRepository tenantRepository,
        WorkflowClientAppRepository clientRepository,
        PasswordEncoder passwordEncoder,
        SecretCipher secretCipher,
        @Value("${workflow.security.actor-signing-secret:}") String environmentSigningSecret,
        @Value("${workflow.security.admin-username}") String adminUsername,
        @Value("${workflow.security.service-username}") String serviceUsername
    ) {
        this.tenantRepository = tenantRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.secretCipher = secretCipher;
        this.environmentSigningSecret =
            environmentSigningSecret == null ? "" : environmentSigningSecret.trim();
        this.reservedClientIds = Set.of(adminUsername, serviceUsername);
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAllByOrderByKeyAsc().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        String key = normalizeTenantKey(request.key());
        if (tenantRepository.existsById(key)) {
            throw new WorkflowException(HttpStatus.CONFLICT, "Tenant already exists: " + key);
        }
        WorkflowTenant tenant = new WorkflowTenant();
        tenant.setKey(key);
        tenant.setName(request.name().trim());
        tenant.setActive(true);
        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional
    public TenantResponse updateTenant(String key, UpdateTenantRequest request) {
        WorkflowTenant tenant = getTenant(normalizeTenantKey(key));
        if (WorkflowTenant.DEFAULT_TENANT.equals(tenant.getKey()) && !request.active()) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "The DEFAULT tenant is used by the environment accounts and cannot be deactivated.");
        }
        tenant.setName(request.name().trim());
        tenant.setActive(request.active());
        return toResponse(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<ClientAppResponse> listClients(String tenantKey) {
        WorkflowTenant tenant = getTenant(normalizeTenantKey(tenantKey));
        return clientRepository.findByTenantKeyOrderByClientIdAsc(tenant.getKey()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public IssuedCredentialsResponse createClient(
        String tenantKey,
        CreateClientAppRequest request
    ) {
        WorkflowTenant tenant = getTenant(normalizeTenantKey(tenantKey));
        String clientId = request.clientId().trim();
        if (reservedClientIds.contains(clientId) || clientRepository.existsByClientId(clientId)) {
            throw new WorkflowException(HttpStatus.CONFLICT,
                "Client ID is already in use: " + clientId);
        }

        WorkflowClientApp client = new WorkflowClientApp();
        client.setTenantKey(tenant.getKey());
        client.setClientId(clientId);
        client.setKind(request.kind());
        client.setActive(true);
        return issueCredentials(client, "Client created.");
    }

    @Transactional
    public IssuedCredentialsResponse rotateCredentials(String clientId) {
        WorkflowClientApp client = getClient(clientId);
        client.setCredentialsRotatedAt(Instant.now());
        return issueCredentials(client, "Credentials rotated. The previous credentials no longer work.");
    }

    @Transactional
    public ClientAppResponse updateClient(String clientId, UpdateClientAppRequest request) {
        WorkflowClientApp client = getClient(clientId);
        client.setActive(request.active());
        return toResponse(clientRepository.save(client));
    }

    /**
     * The HMAC secret the given caller signs transition requests with: the
     * environment secret for the environment service account, otherwise the
     * client app's own decrypted secret.
     */
    @Transactional(readOnly = true)
    public String signingSecretFor(WorkflowPrincipal principal) {
        if (principal.environmentAccount()) {
            return environmentSigningSecret;
        }
        WorkflowClientApp client = getClient(principal.clientId());
        if (client.getSigningSecretEncrypted() == null) {
            return "";
        }
        return secretCipher.decrypt(client.getSigningSecretEncrypted(), client.getClientId());
    }

    private IssuedCredentialsResponse issueCredentials(WorkflowClientApp client, String message) {
        String password = SecretGenerator.generate();
        String signingSecret = null;
        client.setPasswordHash(passwordEncoder.encode(password));
        if (client.getKind() == ClientAppKind.SERVICE) {
            signingSecret = SecretGenerator.generate();
            client.setSigningSecretEncrypted(
                secretCipher.encrypt(signingSecret, client.getClientId()));
        }
        WorkflowClientApp saved = clientRepository.save(client);
        return new IssuedCredentialsResponse(saved.getClientId(), saved.getTenantKey(),
            saved.getKind(), password, signingSecret,
            message + " Store these values now; they cannot be retrieved again.");
    }

    private WorkflowTenant getTenant(String key) {
        return tenantRepository.findById(key)
            .orElseThrow(() -> new WorkflowException(HttpStatus.NOT_FOUND, "Tenant not found: " + key));
    }

    private WorkflowClientApp getClient(String clientId) {
        String normalized = clientId == null ? "" : clientId.trim();
        return clientRepository.findByClientId(normalized)
            .orElseThrow(() -> new WorkflowException(
                HttpStatus.NOT_FOUND, "Client app not found: " + normalized));
    }

    private String normalizeTenantKey(String key) {
        String normalized = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        if (!TENANT_KEY_PATTERN.matcher(normalized).matches()) {
            throw new WorkflowException(
                "Invalid tenant key. Use letters, numbers and underscores; "
                    + "the first character must be a letter.");
        }
        return normalized;
    }

    private TenantResponse toResponse(WorkflowTenant tenant) {
        return new TenantResponse(tenant.getKey(), tenant.getName(), tenant.isActive(),
            tenant.getCreatedAt(), tenant.getUpdatedAt());
    }

    private ClientAppResponse toResponse(WorkflowClientApp client) {
        return new ClientAppResponse(client.getClientId(), client.getTenantKey(), client.getKind(),
            client.isActive(), client.getCreatedAt(), client.getCredentialsRotatedAt());
    }
}
