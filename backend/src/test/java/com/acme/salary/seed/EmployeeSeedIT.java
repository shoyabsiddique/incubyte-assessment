package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import com.acme.salary.seed.application.SeedOutcome;
import com.acme.salary.seed.domain.SeedEmployee;
import com.acme.salary.seed.domain.SeedGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class EmployeeSeedIT extends AbstractMariaDbIntegrationTest {

    private static final long SEED = 20261117L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ExchangeRateQuery rates;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM compensation_record");
        jdbc.update("DELETE FROM employee");
    }

    private EmployeeSeedRunner runner() {
        return new EmployeeSeedRunner(jdbc, new TransactionTemplate(transactions), rates, SEED);
    }

    @Test
    void seedsTenThousandEmployeesInTheRightCountriesWithinAMinute() {
        Instant start = Instant.now();

        SeedOutcome outcome = runner().seed();

        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(60));
        assertThat(outcome.skipped()).isFalse();
        assertThat(outcome.employees()).isEqualTo(10_000);
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(10_000);
        Map<String, Object> byCountry = new java.util.HashMap<>();
        jdbc.query("SELECT country, COUNT(*) c FROM employee GROUP BY country", rs -> {
            byCountry.put(rs.getString(1), rs.getLong(2));
        });
        assertThat(byCountry).containsExactlyInAnyOrderEntriesOf(Map.of(
                "INDIA", 3500L, "USA", 2500L, "UK", 1000L, "GERMANY", 800L,
                "SINGAPORE", 700L, "UAE", 600L, "CANADA", 500L, "AUSTRALIA", 400L));
    }

    @Test
    void everyEmployeeHasExactlyOneOpenRecordInTheirCountrysCurrency() {
        runner().seed();

        assertThat(count("SELECT COUNT(*) FROM employee e WHERE (SELECT COUNT(*) FROM compensation_record c "
                        + "WHERE c.employee_id = e.id AND c.effective_end IS NULL) <> 1"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                        + "WHERE (e.country = 'INDIA' AND c.currency <> 'INR') OR (e.country = 'UK' AND c.currency <> 'GBP') "
                        + "OR (e.country = 'GERMANY' AND c.currency <> 'EUR') OR (e.country = 'USA' AND c.currency <> 'USD')"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM compensation_record")).isGreaterThan(10_000);
    }

    @Test
    void runningAgainOnAPopulatedDatabaseChangesNothing() throws Exception {
        runner().seed();
        String before = contentHash();

        SeedOutcome second = runner().seed();

        assertThat(second.skipped()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(10_000);
        assertThat(contentHash()).isEqualTo(before);
    }

    @Test
    void theSameSeedProducesIdenticalDataOnAFreshDatabase() throws Exception {
        runner().seed();
        String first = contentHash();
        clean();

        runner().seed();

        assertThat(contentHash()).isEqualTo(first);
    }

    @Test
    void aFailureHalfWayThroughLeavesNothingBehind() {
        List<SeedEmployee> good = SeedGenerator.generate(SEED, rates.snapshot());
        List<SeedEmployee> broken = new ArrayList<>(good.subList(0, 1_200));
        broken.add(good.get(0)); // same employee id and email again, rejected by the database
        EmployeeSeedRunner failing =
                new EmployeeSeedRunner(jdbc, new TransactionTemplate(transactions), rates, snapshot -> broken);

        assertThatThrownBy(failing::seed).isInstanceOf(RuntimeException.class);

        assertThat(count("SELECT COUNT(*) FROM employee")).isZero();
        assertThat(count("SELECT COUNT(*) FROM compensation_record")).isZero();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private String contentHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        jdbc.query("SELECT id, employee_id, first_name, last_name, work_email, job_title, department, country, city, "
                        + "employment_type, hire_date, status FROM employee ORDER BY employee_id", rs -> {
            for (int i = 1; i <= 12; i++) {
                digest.update(String.valueOf(rs.getObject(i)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
            }
        });
        jdbc.query("SELECT id, employee_id, amount, currency, effective_start, effective_end FROM compensation_record "
                        + "ORDER BY employee_id, effective_start", rs -> {
            for (int i = 1; i <= 6; i++) {
                digest.update(String.valueOf(rs.getObject(i)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
            }
        });
        return HexFormat.of().formatHex(digest.digest());
    }
}
