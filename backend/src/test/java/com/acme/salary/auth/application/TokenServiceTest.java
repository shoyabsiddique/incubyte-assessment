package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.auth.api.UserIdentity;
import com.acme.salary.auth.domain.JwtSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

    private static final JwtSecret SECRET = JwtSecret.of("unit-test-secret-0123456789-abcdefghij");
    private static final UserIdentity USER =
            new UserIdentity(UUID.fromString("11111111-2222-3333-4444-555555555555"), "hr@acme.example", "HR One");

    private final JwtDecoder decoder = JwtConfig.decoder(SECRET);

    private TokenService serviceAt(Instant now) {
        return new TokenService(JwtConfig.encoder(SECRET), Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1));
    }

    @Test
    void issuesATokenCarryingTheUserIdAndEmail() {
        TokenService service = serviceAt(Instant.now());

        IssuedToken token = service.issue(USER);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("11111111-2222-3333-4444-555555555555");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("hr@acme.example");
    }

    @Test
    void expiresExactlyOneHourAfterIssue() {
        Instant now = Instant.now();

        IssuedToken token = serviceAt(now).issue(USER);
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(3600);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void anExpiredTokenIsRejected() {
        IssuedToken old = serviceAt(Instant.now().minus(Duration.ofHours(3))).issue(USER);

        assertThatThrownBy(() -> decoder.decode(old.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = serviceAt(Instant.now()).issue(USER).value();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        JwtSecret other = JwtSecret.of("some-other-secret-0123456789-abcdefghij");
        String foreign = new TokenService(JwtConfig.encoder(other), Clock.systemUTC(), Duration.ofHours(1))
                .issue(USER)
                .value();

        assertThatThrownBy(() -> decoder.decode(foreign)).isInstanceOf(JwtException.class);
    }
}
