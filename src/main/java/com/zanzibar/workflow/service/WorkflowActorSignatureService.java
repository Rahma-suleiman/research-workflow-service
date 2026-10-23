package com.zanzibar.workflow.service;

import com.zanzibar.workflow.dto.request.ExecuteTransitionRequest;
import com.zanzibar.workflow.exception.WorkflowException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Verifies that actor identity, role, trigger, and transition details were
 * signed by the trusted calling backend. HTTP Basic alone only proves
 * which service account called this API; it does not prove the end-user role.
 */
@Service
public class WorkflowActorSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final String secret;

    public WorkflowActorSignatureService(
        @Value("${workflow.security.actor-signing-secret:}") String secret
    ) {
        this.secret = secret == null ? "" : secret.trim();
    }

    public void verify(ExecuteTransitionRequest request, String suppliedSignature) {
        verify(request, suppliedSignature, secret);
    }

    /** Verifies against a specific caller's secret (each client app has its own). */
    public void verify(
        ExecuteTransitionRequest request,
        String suppliedSignature,
        String secret
    ) {
        if (secret == null || secret.length() < 32) {
            throw new WorkflowException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Actor signature verification is not configured for this client. "
                    + "Configure a signing secret of at least 32 characters."
            );
        }
        if (suppliedSignature == null || suppliedSignature.isBlank()) {
            throw new WorkflowException(
                HttpStatus.UNAUTHORIZED,
                "A trusted actor signature is required."
            );
        }

        byte[] expected = sign(canonicalPayload(request), secret);
        byte[] supplied;
        try {
            supplied = Base64.getUrlDecoder().decode(suppliedSignature.trim());
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                HttpStatus.UNAUTHORIZED,
                "The actor signature is invalid."
            );
        }

        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new WorkflowException(
                HttpStatus.UNAUTHORIZED,
                "The actor signature is invalid or does not match this request."
            );
        }
    }

    /** Used by trusted integration code and tests to produce the same signature. */
    public String signRequest(ExecuteTransitionRequest request) {
        return signRequest(request, secret);
    }

    public String signRequest(ExecuteTransitionRequest request, String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "The actor signing secret must contain at least 32 characters."
            );
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(sign(canonicalPayload(request), secret));
    }

    private byte[] sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign workflow actor assertion.", exception);
        }
    }

    private String canonicalPayload(ExecuteTransitionRequest request) {
        List<String> values = List.of(
            safe(request.workflowDefinitionId()),
            safe(request.workflowVersion()),
            safe(request.subjectId()),
            normalize(request.currentStatus()),
            normalize(request.actionKey()),
            safe(request.triggerType()),
            safe(request.actorId()),
            normalize(request.actorRole()),
            safe(request.comments()),
            safe(request.idempotencyKey())
        );
        // Length-prefix each value so separators or newlines cannot make two
        // different requests produce the same canonical representation.
        StringBuilder canonical = new StringBuilder("workflow-actor-v1|");
        for (String value : values) {
            canonical.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':').append(value);
        }
        return canonical.toString();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
            .replaceFirst("^ROLE_", "");
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
