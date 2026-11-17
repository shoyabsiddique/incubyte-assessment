package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The application starts with {@code ddl-auto=validate}, so reaching this test already proves
 * the entities match the schema. This checks Flyway really ran the baseline.
 */
class FlywayBaselineIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedTheBaselineMigrationSuccessfully() {
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
