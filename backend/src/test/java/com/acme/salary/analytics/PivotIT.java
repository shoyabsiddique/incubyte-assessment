package com.acme.salary.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** The dashboard's generic pivot endpoint over the seeded 10,000 employees, checked against plain SQL. */
class PivotIT extends AbstractMariaDbIntegrationTest {

    private static JdbcTemplate staticJdbc;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ExchangeRateQuery rates;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private ApiClient api;

    @BeforeEach
    void seedOnceAndSignIn() throws Exception {
        staticJdbc = jdbc;
        new EmployeeSeedRunner(jdbc, new TransactionTemplate(transactions), rates, 20261117L).seed();
        api = new ApiClient(port, users, encoder);
    }

    @AfterAll
    static void removeSeededData() {
        if (staticJdbc != null) {
            staticJdbc.update("DELETE FROM compensation_record");
            staticJdbc.update("DELETE FROM employee");
        }
    }

    private JsonNode ok(String query) throws Exception {
        Reply reply = api.get("/api/v1/analytics/pivot?" + query);
        assertThat(reply.status()).as(query).isEqualTo(200);
        return reply.body();
    }

    private static long sumOf(JsonNode series) {
        long total = 0;
        for (JsonNode point : series.get("points")) {
            total += point.get("count").asLong();
        }
        return total;
    }

    @Test
    void headcountByCountryCoversEveryActiveEmployee() throws Exception {
        JsonNode body = ok("x=country");

        assertThat(body.get("categories")).hasSize(8);
        assertThat(body.get("categories").get(0).get("label").asString()).isEqualTo("Australia");
        assertThat(body.get("series")).hasSize(1);
        assertThat(body.get("series").get(0).get("label").asString()).isEqualTo("All");
        assertThat(sumOf(body.get("series").get(0)))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE status = 'ACTIVE'", Long.class));
    }

    @Test
    void averageSalaryByDepartmentMatchesPlainSql() throws Exception {
        JsonNode body = ok("x=department&measure=salaryUsd&aggregation=average");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        JsonNode categories = body.get("categories");
        JsonNode points = body.get("series").get(0).get("points");
        for (int i = 0; i < categories.size(); i++) {
            String department = categories.get(i).get("key").asString();
            BigDecimal expected = jdbc.queryForObject(
                    "SELECT AVG(c.amount * r.rate_to_usd) FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                            + "JOIN exchange_rate r ON r.currency = c.currency WHERE e.status = 'ACTIVE' AND e.department = ? "
                            + "AND c.effective_start <= ? AND (c.effective_end IS NULL OR c.effective_end >= ?)",
                    BigDecimal.class,
                    department,
                    today,
                    today);
            BigDecimal got = new BigDecimal(points.get(i).get("value").asString());
            assertThat(got.subtract(expected.setScale(2, RoundingMode.HALF_UP)).abs())
                    .as(department)
                    .isLessThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    @Test
    void aSecondFieldSplitsEveryCategoryIntoSeries() throws Exception {
        JsonNode body = ok("x=country&series=employmentType&measure=headcount");

        assertThat(body.get("series").size()).isGreaterThan(1);
        for (JsonNode series : body.get("series")) {
            assertThat(series.get("points")).hasSize(8);
        }
        long total = 0;
        for (JsonNode series : body.get("series")) {
            total += sumOf(series);
        }
        assertThat(total).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE status = 'ACTIVE'", Long.class));
        assertThat(body.get("series").get(0).get("label").asString()).doesNotContain("_");
    }

    @Test
    void filtersAndOtherDimensionsWork() throws Exception {
        JsonNode india = ok("x=department&country=INDIA");
        assertThat(sumOf(india.get("series").get(0)))
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE status = 'ACTIVE' AND country = 'INDIA'", Long.class));

        assertThat(ok("x=hireYear&measure=tenureYears&aggregation=maximum").get("categories").size()).isGreaterThan(3);
        assertThat(ok("x=city&measure=salaryUsd&aggregation=median").get("categories").size()).isGreaterThan(3);
        assertThat(ok("x=jobTitle&measure=salaryUsd&aggregation=sum").get("categories").size()).isGreaterThan(5);
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        for (String bad : new String[] {
            "", "x=salary", "x=country&series=country", "x=country&series=nope", "x=country&measure=nope",
            "x=country&aggregation=nope", "x=country&measure=headcount&aggregation=sum", "x=country&country=ATLANTIS",
            "x=country&department=Nope"
        }) {
            Reply reply = api.get("/api/v1/analytics/pivot?" + bad);
            assertThat(reply.status()).as(bad).isEqualTo(400);
        }
        assertThat(api.get("/api/v1/analytics/pivot?x=salary").body().get("code").asString()).isEqualTo("INVALID_DIMENSION");
        assertThat(api.withoutToken("GET", "/api/v1/analytics/pivot?x=country", null).status()).isEqualTo(401);
    }

    @Test
    void respondsWithinTheTarget() throws Exception {
        ok("x=country&series=department&measure=salaryUsd&aggregation=average");
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            ok("x=country&series=department&measure=salaryUsd&aggregation=average");
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("ANALYTICS-PERF pivot=" + best + "ms");
        assertThat(best).isLessThan(500);
    }
}
