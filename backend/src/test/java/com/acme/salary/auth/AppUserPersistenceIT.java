package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AppUserPersistenceIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAndFindsAUserByEmail() {
        String email = "find-" + UUID.randomUUID() + "@acme.example";
        UUID id = UUID.randomUUID();
        users.save(new AppUserEntity(id, email, "$2a$04$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234", "Finder"));

        assertThat(users.findByEmail(email)).get().satisfies(found -> {
            assertThat(found.getId()).isEqualTo(id);
            assertThat(found.getDisplayName()).isEqualTo("Finder");
        });
    }

    @Test
    void theDatabaseTreatsEmailsAsUniqueIgnoringCase() {
        String local = "dup-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, email, password_hash, display_name) VALUES (?, ?, 'x', 'A')",
                UUID.randomUUID().toString(), local + "@acme.example");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO app_user (id, email, password_hash, display_name) VALUES (?, ?, 'x', 'B')",
                        UUID.randomUUID().toString(), (local + "@ACME.example").toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
