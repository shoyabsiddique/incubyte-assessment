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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** Salary statistics and pay extremes over real HTTP against the seeded 10,000 employees, checked against plain SQL. */
class AnalyticsIT extends AbstractMariaDbIntegrationTest {

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

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode ok(String path) throws Exception {
        Reply reply = api.get(path);
        assertThat(reply.status()).as(path).isEqualTo(200);
        return reply.body();
    }

    private static final String IN_FORCE =
            " AND c.effective_start <= ? AND (c.effective_end IS NULL OR c.effective_end >= ?)";

    private Map<String, Object> sqlStats(String where, Object... args) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Object> all = new ArrayList<>(List.of(args));
        all.add(today);
        all.add(today);
        return jdbc.queryForMap(
                "SELECT COUNT(*) AS n, AVG(c.amount * r.rate_to_usd) AS average, MIN(c.amount * r.rate_to_usd) AS low, "
                        + "MAX(c.amount * r.rate_to_usd) AS high, SUM(c.amount * r.rate_to_usd) AS total "
                        + "FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                        + "JOIN exchange_rate r ON r.currency = c.currency WHERE e.status = 'ACTIVE' AND " + where + IN_FORCE,
                all.toArray());
    }

    private static void assertMoney(JsonNode money, Object expected) {
        BigDecimal wanted = ((BigDecimal) expected).setScale(2, RoundingMode.HALF_UP);
        assertThat(new BigDecimal(money.get("amount").asString()).subtract(wanted).abs())
                .as("expected %s got %s", wanted, money.get("amount").asString())
                .isLessThanOrEqualTo(new BigDecimal("0.01"));
        assertThat(money.get("currency").asString()).isEqualTo("USD");
    }

    @Test
    void statisticsByCountryCoverEveryActiveEmployeeAndMatchPlainSql() throws Exception {
        JsonNode body = ok("/api/v1/analytics/salary-stats?groupBy=country");

        assertThat(body.get("groupBy").asString()).isEqualTo("country");
        long counted = 0;
        for (JsonNode group : body.get("groups")) {
            counted += group.get("usd").get("count").asLong();
            assertThat(group.get("local").isNull()).isTrue();
        }
        assertThat(counted).isEqualTo(sqlCountActive());
        JsonNode india = group(body, "INDIA");
        Map<String, Object> expected = sqlStats("e.country = ?", "INDIA");
        assertThat(india.get("label").asString()).isEqualTo("India");
        assertThat(india.get("usd").get("count").asLong()).isEqualTo(((Number) expected.get("n")).longValue());
        assertMoney(india.get("usd").get("average"), expected.get("average"));
        assertMoney(india.get("usd").get("minimum"), expected.get("low"));
        assertMoney(india.get("usd").get("maximum"), expected.get("high"));
        assertMoney(india.get("usd").get("total"), expected.get("total"));
    }

    private long sqlCountActive() {
        return ((Number) sqlStats("1 = 1").get("n")).longValue();
    }

    private static JsonNode group(JsonNode body, String key) {
        for (JsonNode group : body.get("groups")) {
            if (group.get("key").asString().equals(key)) {
                return group;
            }
        }
        throw new AssertionError("no group " + key);
    }

    @Test
    void filteringByJobTitleGivesThatTitleInEveryCountry() throws Exception {
        String title = jdbc.queryForObject(
                "SELECT job_title FROM employee WHERE status = 'ACTIVE' GROUP BY job_title ORDER BY COUNT(*) DESC LIMIT 1", String.class);

        JsonNode body = ok("/api/v1/analytics/salary-stats?groupBy=country&jobTitle=" + enc(title));

        assertThat(body.get("groups").size()).isGreaterThan(1);
        for (JsonNode group : body.get("groups")) {
            Map<String, Object> expected = sqlStats("e.country = ? AND e.job_title = ?", group.get("key").asString(), title);
            assertThat(group.get("usd").get("count").asLong()).isEqualTo(((Number) expected.get("n")).longValue());
            assertMoney(group.get("usd").get("average"), expected.get("average"));
        }
    }

    @Test
    void aCountryFilterAddsLocalCurrencyFigures() throws Exception {
        JsonNode body = ok("/api/v1/analytics/salary-stats?groupBy=department&country=INDIA");

        assertThat(body.get("groups").size()).isGreaterThan(0);
        for (JsonNode group : body.get("groups")) {
            assertThat(group.get("local").get("average").get("currency").asString()).isEqualTo("INR");
            assertThat(group.get("usd").get("average").get("currency").asString()).isEqualTo("USD");
        }
    }

    @Test
    void groupingByDepartmentAndJobTitleWorks() throws Exception {
        assertThat(ok("/api/v1/analytics/salary-stats?groupBy=department").get("groups").size()).isGreaterThan(1);
        assertThat(ok("/api/v1/analytics/salary-stats?groupBy=jobTitle").get("groupBy").asString()).isEqualTo("jobTitle");
        assertThat(ok("/api/v1/analytics/salary-stats").get("groupBy").asString()).isEqualTo("country");
    }

    @Test
    void invalidGroupingsAndFiltersAreRejected() throws Exception {
        for (String bad : new String[] {"groupBy=salary", "country=ATLANTIS", "department=Nope", "jobTitle=Nope"}) {
            Reply reply = api.get("/api/v1/analytics/salary-stats?" + bad);
            assertThat(reply.status()).as(bad).isEqualTo(400);
        }
        assertThat(api.get("/api/v1/analytics/salary-stats?groupBy=salary").body().get("code").asString())
                .isEqualTo("INVALID_GROUP_BY");
        assertThat(api.withoutToken("GET", "/api/v1/analytics/salary-stats", null).status()).isEqualTo(401);
        assertThat(api.withoutToken("GET", "/api/v1/analytics/pay-extremes", null).status()).isEqualTo(401);
    }

    @Test
    void anEditedExchangeRateChangesTheNextAnswer() throws Exception {
        JsonNode before = group(ok("/api/v1/analytics/salary-stats?groupBy=country"), "INDIA");
        JsonNode rate = null;
        for (JsonNode entry : api.get("/api/v1/exchange-rates").body()) {
            if (entry.get("currency").asString().equals("INR")) {
                rate = entry;
            }
        }
        try {
            assertThat(api.put("/api/v1/exchange-rates/INR", "{\"rate\":\"0.024\",\"version\":" + rate.get("version").asLong() + "}").status())
                    .isEqualTo(200);

            JsonNode after = group(ok("/api/v1/analytics/salary-stats?groupBy=country"), "INDIA");

            BigDecimal was = new BigDecimal(before.get("usd").get("average").get("amount").asString());
            BigDecimal now = new BigDecimal(after.get("usd").get("average").get("amount").asString());
            assertThat(now.divide(was, 2, RoundingMode.HALF_UP)).isEqualByComparingTo("2.00");
        } finally {
            jdbc.update("UPDATE exchange_rate SET rate_to_usd = 0.012, version = ? WHERE currency = 'INR'", rate.get("version").asLong());
            jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        }
    }

    @Test
    void payExtremesOfADepartmentAreOrderedAndDoNotOverlap() throws Exception {
        String department = jdbc.queryForObject(
                "SELECT department FROM employee WHERE status = 'ACTIVE' GROUP BY department ORDER BY COUNT(*) DESC LIMIT 1", String.class);

        JsonNode body = ok("/api/v1/analytics/pay-extremes?n=3&department=" + enc(department));

        assertThat(body.get("departments")).hasSize(1);
        JsonNode result = body.get("departments").get(0);
        assertThat(result.get("department").asString()).isEqualTo(department);
        assertThat(result.get("highest")).hasSize(3);
        assertThat(result.get("lowest")).hasSize(3);
        assertDescending(result.get("highest"));
        List<String> seen = new ArrayList<>();
        result.get("highest").forEach(p -> seen.add(p.get("employeeId").asString()));
        result.get("lowest").forEach(p -> seen.add(p.get("employeeId").asString()));
        assertThat(seen).doesNotHaveDuplicates();
        BigDecimal lowestTop = usd(result.get("lowest").get(2));
        BigDecimal lowestFirst = usd(result.get("lowest").get(0));
        assertThat(lowestFirst).isLessThanOrEqualTo(lowestTop);
        assertThat(usd(result.get("highest").get(2))).isGreaterThanOrEqualTo(lowestTop);
        JsonNode top = result.get("highest").get(0);
        assertThat(top.get("id").asString()).isNotBlank();
        assertThat(top.get("salary").get("currency").asString()).isNotBlank();
        assertThat(top.get("name").asString()).isNotBlank();
    }

    private static void assertDescending(JsonNode people) {
        for (int i = 1; i < people.size(); i++) {
            assertThat(usd(people.get(i - 1))).isGreaterThanOrEqualTo(usd(people.get(i)));
        }
    }

    private static BigDecimal usd(JsonNode person) {
        return new BigDecimal(person.get("salaryUsd").get("amount").asString());
    }

    @Test
    void withoutADepartmentTheResultIsGroupedPerDepartmentAndNIsChecked() throws Exception {
        JsonNode body = ok("/api/v1/analytics/pay-extremes");

        assertThat(body.get("departments").size()).isGreaterThan(1);
        for (JsonNode department : body.get("departments")) {
            assertThat(department.get("highest").size()).isLessThanOrEqualTo(5);
        }
        assertThat(api.get("/api/v1/analytics/pay-extremes?n=26").body().get("code").asString()).isEqualTo("N_TOO_LARGE");
        assertThat(api.get("/api/v1/analytics/pay-extremes?n=0").status()).isEqualTo(400);
        assertThat(api.get("/api/v1/analytics/pay-extremes?n=x").status()).isEqualTo(400);
        assertThat(api.get("/api/v1/analytics/pay-extremes?department=Nope").status()).isEqualTo(400);
    }

    @Test
    void recordsHowLongTheAnalyticsEndpointsTakeOnTenThousandEmployees() throws Exception {
        ok("/api/v1/analytics/salary-stats?groupBy=country"); // warm up
        long best = Long.MAX_VALUE;
        long extremes = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            ok("/api/v1/analytics/salary-stats?groupBy=jobTitle");
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
            start = System.nanoTime();
            ok("/api/v1/analytics/pay-extremes");
            extremes = Math.min(extremes, (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("ANALYTICS-PERF salary-stats(jobTitle)=" + best + "ms pay-extremes=" + extremes + "ms");
        assertThat(best).as("salary-stats best of five, ms").isLessThan(500);
        assertThat(extremes).as("pay-extremes best of five, ms").isLessThan(500);
    }
}
