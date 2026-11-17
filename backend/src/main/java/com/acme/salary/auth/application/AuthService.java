package com.acme.salary.auth.application;

import com.acme.salary.auth.api.UserIdentity;
import com.acme.salary.auth.domain.InvalidCredentialsException;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // BCrypt only uses the first 72 bytes; refusing longer input also bounds the hashing work.
    private static final int MAX_PASSWORD_BYTES = 72;
    // The column holds at most 254 characters, so a longer email cannot belong to any account.
    private static final int MAX_EMAIL_LENGTH = 254;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final String unknownUserHash;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.unknownUserHash = encoder.encode("password-nobody-has");
    }

    public LoginResult login(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Optional<AppUserEntity> user = normalized.isEmpty() || normalized.length() > MAX_EMAIL_LENGTH
                ? Optional.empty()
                : users.findByEmail(normalized);

        String candidate = password == null ? "" : password;
        if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            candidate = "";
        }

        // Always exactly one comparison, against a dummy hash for unknown users, so timing does not
        // reveal whether an email exists.
        String hash = user.map(AppUserEntity::getPasswordHash).orElse(unknownUserHash);
        boolean matches = encoder.matches(candidate, hash);

        if (user.isEmpty() || !matches || candidate.isEmpty()) {
            throw new InvalidCredentialsException();
        }

        AppUserEntity found = user.get();
        UserIdentity identity = new UserIdentity(found.getId(), found.getEmail(), found.getDisplayName());
        return new LoginResult(tokens.issue(identity), identity);
    }
}
