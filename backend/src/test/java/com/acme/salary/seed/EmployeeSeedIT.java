package com.acme.salary.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import com.acme.salary.seed.application.SeedOutcome;
import com.acme.salary.seed.domain.SeedEmployee;
import com.acme.salary.seed.domain.SeedGenerator;
import com.acme.salary.shared.domain.Country;
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

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

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
        jdbc.query("SELECT e.country, c.currency FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                + "GROUP BY e.country, c.currency", rs -> {
            Country country = Country.valueOf(rs.getString(1));
            assertThat(rs.getString(2)).as(country.name()).isEqualTo(country.currency().getCurrencyCode());
        });
        assertThat(count("SELECT COUNT(DISTINCT country) FROM employee")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM compensation_record")).isGreaterThan(10_000);
    }

    @Test
    void storedRowsMatchTheGeneratedDataFieldByField() {
        runner().seed();
        List<SeedEmployee> generated = SeedGenerator.generate(SEED, rates.snapshot());

        // first, a chunk boundary (500 rows per statement), and last
        for (int index : new int[] {0, 499, 500, 501, 4_999, 9_999}) {
            SeedEmployee expected = generated.get(index);
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM employee WHERE employee_id = ?", expected.employeeId());

            assertThat(row.get("id")).isEqualTo(expected.id().toString());
            assertThat(row.get("first_name")).isEqualTo(expected.firstName());
            assertThat(row.get("last_name")).isEqualTo(expected.lastName());
            assertThat(row.get("work_email")).isEqualTo(expected.workEmail());
            assertThat(row.get("job_title")).isEqualTo(expected.jobTitle());
            assertThat(row.get("department")).isEqualTo(expected.department());
            assertThat(row.get("country")).isEqualTo(expected.country().name());
            assertThat(row.get("city")).isEqualTo(expected.city());
            assertThat(row.get("employment_type")).isEqualTo(expected.employmentType().name());
            assertThat(row.get("hire_date").toString()).isEqualTo(expected.hireDate().toString());
            assertThat(row.get("status")).isEqualTo(expected.status().name());

            List<Map<String, Object>> records = jdbc.queryForList(
                    "SELECT * FROM compensation_record WHERE employee_id = ? ORDER BY effective_start", expected.id().toString());
            assertThat(records).hasSameSizeAs(expected.compensation());
            for (int i = 0; i < records.size(); i++) {
                var stored = records.get(i);
                var wanted = expected.compensation().stream()
                        .sorted(java.util.Comparator.comparing(c -> c.effectiveStart()))
                        .toList()
                        .get(i);
                assertThat(stored.get("id")).isEqualTo(wanted.id().toString());
                assertThat(new java.math.BigDecimal(stored.get("amount").toString())).isEqualByComparingTo(wanted.amount());
                assertThat(stored.get("currency")).isEqualTo(wanted.currency().getCurrencyCode());
                assertThat(stored.get("effective_start").toString()).isEqualTo(wanted.effectiveStart().toString());
                assertThat(String.valueOf(stored.get("effective_end"))).isEqualTo(String.valueOf(wanted.effectiveEnd()));
            }
        }
    }

    @Test
    void theRunnerIsNotPartOfTheApplicationUnlessTheSeedProfileIsActive() {
        assertThat(applicationContext.getBeansOfType(EmployeeSeedRunner.class)).isEmpty();
    }

    @Test
    void whenAnotherProcessSeedsFirstTheLaterRunnerStepsAsideInsteadOfFailing() {
        EmployeeSeedRunner late = new EmployeeSeedRunner(jdbc, new TransactionTemplate(transactions), rates, snapshot -> {
            runner().seed(); // the other process commits its seed while we are about to write ours
            return SeedGenerator.generate(SEED, snapshot);
        });

        SeedOutcome outcome = late.seed();

        assertThat(outcome.skipped()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(10_000);
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
