package com.zanzibar.workflow.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates the passwords and signing secrets the engine issues (256 bits, URL-safe). */
public final class SecretGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private SecretGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
