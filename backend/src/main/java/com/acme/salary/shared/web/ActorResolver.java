package com.acme.salary.shared.web;

import com.acme.salary.shared.domain.Actor;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Who made the request, read from the verified token. */
public final class ActorResolver {

    private ActorResolver() {}

    public static Actor from(Jwt token) {
        String email = token.getClaimAsString("email");
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("no email");
            }
            return new Actor(UUID.fromString(token.getSubject()), email);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new DomainException(ErrorKind.UNAUTHENTICATED, "UNAUTHENTICATED", "The token does not identify a user");
        }
    }
}
