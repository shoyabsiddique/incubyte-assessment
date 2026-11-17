package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** With the seed profile switched on, starting the application itself loads the demo workforce. */
@ActiveProfiles("seed")
@TestPropertySource(properties = "app.seed.hr-password=Profile-Test-Pass-1")
class EmployeeSeedProfileIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startingWithTheSeedProfileRunsTheSeedAndCreatesTheDemoAccounts() {
        assertThat(context.getBeansOfType(EmployeeSeedRunner.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class)).isEqualTo(10_000L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class)).isEqualTo(2L);
    }
}
