package com.zanzibar.workflow.security;

import com.zanzibar.workflow.exception.WorkflowException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(
        "test-only-encryption-key-32bytes".getBytes(StandardCharsets.UTF_8));

    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void decryptsWhatItEncrypted() {
        String stored = cipher.encrypt("signing-secret", "client-a");
        assertNotEquals("signing-secret", stored);
        assertEquals("signing-secret", cipher.decrypt(stored, "client-a"));
    }

    @Test
    void encryptingTheSameValueTwiceGivesDifferentCiphertexts() {
        assertNotEquals(cipher.encrypt("same", "client-a"), cipher.encrypt("same", "client-a"));
    }

    @Test
    void ciphertextCopiedToAnotherClientCannotBeDecrypted() {
        String stored = cipher.encrypt("signing-secret", "client-a");
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(stored, "client-b"));
    }

    @Test
    void unconfiguredCipherRefusesWithServiceUnavailable() {
        SecretCipher unconfigured = new SecretCipher("");
        assertFalse(unconfigured.isConfigured());
        WorkflowException error = assertThrows(WorkflowException.class,
            () -> unconfigured.encrypt("value", "client-a"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
    }

    @Test
    void keyOfWrongLengthIsRejectedAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new SecretCipher(shortKey));
    }
}
