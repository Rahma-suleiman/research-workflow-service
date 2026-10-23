package com.zanzibar.workflow.model;

import com.zanzibar.workflow.enums.ClientAppKind;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Credentials a tenant uses to call the engine. The password is stored as a
 * BCrypt hash; a SERVICE client's HMAC signing secret is stored encrypted.
 */
@Entity
@Table(name = "workflow_client_apps")
public class WorkflowClientApp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_key", nullable = false, length = 60)
    private String tenantKey;

    @Column(name = "client_id", nullable = false, length = 100, unique = true)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientAppKind kind;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "signing_secret_encrypted", columnDefinition = "TEXT")
    private String signingSecretEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "credentials_rotated_at", nullable = false)
    private Instant credentialsRotatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (credentialsRotatedAt == null) {
            credentialsRotatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTenantKey() {
        return tenantKey;
    }

    public void setTenantKey(String tenantKey) {
        this.tenantKey = tenantKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public ClientAppKind getKind() {
        return kind;
    }

    public void setKind(ClientAppKind kind) {
        this.kind = kind;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSigningSecretEncrypted() {
        return signingSecretEncrypted;
    }

    public void setSigningSecretEncrypted(String signingSecretEncrypted) {
        this.signingSecretEncrypted = signingSecretEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCredentialsRotatedAt() {
        return credentialsRotatedAt;
    }

    public void setCredentialsRotatedAt(Instant credentialsRotatedAt) {
        this.credentialsRotatedAt = credentialsRotatedAt;
    }
}
