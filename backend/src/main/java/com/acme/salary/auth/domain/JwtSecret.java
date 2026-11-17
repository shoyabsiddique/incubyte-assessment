package com.acme.salary.auth.domain;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** The key that signs tokens. At least 256 bits, and never shown in logs or messages. */
public final class JwtSecret {

    public static final int MINIMUM_BYTES = 32;

    private final byte[] bytes;

    private JwtSecret(byte[] bytes) {
        this.bytes = bytes;
    }

    public static JwtSecret of(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("The JWT secret is missing; it must be at least 32 bytes");
        }
        if (secret.toLowerCase(Locale.ROOT).startsWith("change-me")) {
            throw new IllegalArgumentException("The JWT secret is still the example placeholder; set a real one");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_BYTES) {
            throw new IllegalArgumentException("The JWT secret is too short; it must be at least 32 bytes");
        }
        return new JwtSecret(bytes);
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public String toString() {
        return "JwtSecret[hidden]";
    }
}
