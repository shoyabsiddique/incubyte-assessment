package com.acme.salary.auth.application;

import com.acme.salary.auth.api.UserIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Issues signed, short-lived access tokens. The subject is the user id; time comes from the Clock. */
public class TokenService {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration timeToLive;

    public TokenService(JwtEncoder encoder, Clock clock, Duration timeToLive) {
        this.encoder = encoder;
        this.clock = clock;
        this.timeToLive = timeToLive;
    }

    public IssuedToken issue(UserIdentity user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .issuedAt(now)
                .expiresAt(now.plus(timeToLive))
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(value, timeToLive.toSeconds());
    }
}
