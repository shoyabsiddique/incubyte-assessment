package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.application.DemoAccountSeeder;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAccountSeederIT extends AbstractMariaDbIntegrationTest {

    private static final String PRIYA = "priya.nair@acme.example";
    private static final String DAVID = "david.okafor@acme.example";

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTheDemoAccounts() {
        jdbc.update("DELETE FROM app_user WHERE email IN (?, ?)", PRIYA, DAVID);
    }

    @Test
    void createsBothDemoAccountsWithHashedPasswordsThatMatch() throws Exception {
        new DemoAccountSeeder(users, encoder, "Seeded-Pass-1").run(null);

        for (String email : new String[] {PRIYA, DAVID}) {
            AppUserEntity user = users.findByEmail(email).orElseThrow();
            assertThat(user.getPasswordHash()).isNotEqualTo("Seeded-Pass-1");
            assertThat(encoder.matches("Seeded-Pass-1", user.getPasswordHash())).isTrue();
        }
    }

    @Test
    void runningTwiceCreatesNothingNewAndChangesNothing() throws Exception {
        DemoAccountSeeder seeder = new DemoAccountSeeder(users, encoder, "Seeded-Pass-1");
        seeder.run(null);
        String firstHash = users.findByEmail(PRIYA).orElseThrow().getPasswordHash();

        seeder.run(null);

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email IN (?, ?)", Integer.class, PRIYA, DAVID);
        assertThat(rows).isEqualTo(2);
        assertThat(users.findByEmail(PRIYA).orElseThrow().getPasswordHash()).isEqualTo(firstHash);
    }

    @Test
    void neverOverwritesAnAccountThatAlreadyExists() throws Exception {
        String existingHash = encoder.encode("Their-Own-Password-9");
        users.save(new AppUserEntity(UUID.randomUUID(), DAVID, existingHash, "David"));

        new DemoAccountSeeder(users, encoder, "Seeded-Pass-1").run(null);

        assertThat(users.findByEmail(DAVID).orElseThrow().getPasswordHash()).isEqualTo(existingHash);
        assertThat(users.findByEmail(PRIYA)).isPresent();
    }
}
