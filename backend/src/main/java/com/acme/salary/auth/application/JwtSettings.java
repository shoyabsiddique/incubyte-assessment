package com.acme.salary.auth.application;

import com.acme.salary.auth.domain.JwtSecret;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security settings. The application refuses to start if the signing secret is missing or under
 * 32 bytes, so a weak key can never reach production.
 */
@ConfigurationProperties("app.security")
public record JwtSettings(
        String jwtSecret,
        @DefaultValue("PT1H") Duration tokenTtl,
        @DefaultValue("10") int bcryptStrength) {

    public JwtSettings {
        JwtSecret.of(jwtSecret);
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.security.token-ttl must be a positive duration");
        }
        if (bcryptStrength < 4 || bcryptStrength > 31) {
            throw new IllegalArgumentException("app.security.bcrypt-strength must be between 4 and 31");
        }
    }

    public JwtSecret secret() {
        return JwtSecret.of(jwtSecret);
    }

    @Override
    public String toString() {
        return "JwtSettings[tokenTtl=" + tokenTtl + ", bcryptStrength=" + bcryptStrength + "]";
    }
}
