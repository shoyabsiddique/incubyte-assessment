package com.acme.salary.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretTest {

    @Test
    void acceptsASecretOfAtLeast32Bytes() {
        JwtSecret secret = JwtSecret.of("a".repeat(32));

        assertThat(secret.bytes()).hasSize(32);
    }

    @Test
    void rejectsASecretShorterThan32Bytes() {
        assertThatThrownBy(() -> JwtSecret.of("a".repeat(31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void rejectsMissingOrBlankSecrets() {
        assertThatThrownBy(() -> JwtSecret.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JwtSecret.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JwtSecret.of(" ".repeat(40))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countsBytesNotCharacters() {
        // 16 two-byte characters are exactly 32 bytes in UTF-8
        assertThat(JwtSecret.of("é".repeat(16)).bytes()).hasSize(32);
        assertThatThrownBy(() -> JwtSecret.of("é".repeat(15))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverRevealsTheSecretWhenPrinted() {
        assertThat(JwtSecret.of("super-secret-value-super-secret-value").toString())
                .doesNotContain("super-secret");
    }
}
