package com.acme.salary.auth.application;

import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo HR Manager accounts when the application runs with the local or seed profile.
 * Passwords are hashed at runtime from configuration, so no credential is ever stored in the
 * repository or a migration. Existing accounts are left untouched, so running it twice is safe.
 */
@Component
@Profile({"local", "seed"})
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoAccountSeeder(
            AppUserRepository users, PasswordEncoder encoder, @Value("${app.seed.hr-password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("priya.nair@acme.example", "Priya Nair");
        createIfMissing("david.okafor@acme.example", "David Okafor");
    }

    private void createIfMissing(String email, String displayName) {
        if (users.existsByEmail(email)) {
            return;
        }
        users.save(new AppUserEntity(UUID.randomUUID(), email, encoder.encode(password), displayName));
        log.info("Created demo HR Manager account {}", email);
    }
}
