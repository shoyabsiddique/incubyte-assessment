package com.acme.salary.shared.web;

import com.acme.salary.shared.domain.Actor;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who made the request, read from the verified token. */
public final class ActorResolver {

    private ActorResolver() {}

    public static Actor from(Jwt token) {
        return new Actor(UUID.fromString(token.getSubject()), token.getClaimAsString("email"));
    }
}
