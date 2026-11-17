package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.json.JsonMapper;

/** One employee's page and salary history over real HTTP, on the full seeded workforce. */
class EmployeeDetailIT extends AbstractMariaDbIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
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

    private final HttpClient client = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void seedOnceAndSignIn() throws Exception {
        staticJdbc = jdbc;
        new EmployeeSeedRunner(jdbc, new TransactionTemplate(transactions), rates, 20261117L).seed();
        String email = "detail-" + UUID.randomUUID() + "@acme.example";
        users.save(new AppUserEntity(UUID.randomUUID(), email, encoder.encode("Detail-Pass-12345"), "Detail Tester"));
        HttpRequest login = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"Detail-Pass-12345\"}"))
                .build();
        token = JSON.readTree(client.send(login, HttpResponse.BodyHandlers.ofString()).body()).get("accessToken").asString();
    }

    @AfterAll
    static void removeSeededData() {
        if (staticJdbc != null) {
            staticJdbc.update("DELETE FROM compensation_record");
            staticJdbc.update("DELETE FROM employee");
        }
    }

    // ---- the employee ----------------------------------------------------------------------------

    @Test
    void showsTheProfileAndCurrentPayOfAKnownEmployee() throws Exception {
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM employee WHERE employee_id = 'EMP-000001'");
        String id = (String) row.get("id");

        JsonNode body = ok("/api/v1/employees/" + id);

        assertThat(body.get("id").asString()).isEqualTo(id);
        assertThat(body.get("employeeId").asString()).isEqualTo("EMP-000001");
        assertThat(body.get("firstName").asString()).isEqualTo(row.get("first_name"));
        assertThat(body.get("lastName").asString()).isEqualTo(row.get("last_name"));
        assertThat(body.get("workEmail").asString()).isEqualTo(row.get("work_email"));
        assertThat(body.get("jobTitle").asString()).isEqualTo(row.get("job_title"));
        assertThat(body.get("department").asString()).isEqualTo(row.get("department"));
        assertThat(body.get("country").asString()).isEqualTo(row.get("country"));
        assertThat(body.get("countryName").asString()).isNotBlank();
        assertThat(body.get("city").asString()).isEqualTo(row.get("city"));
        assertThat(body.get("employmentType").asString()).isEqualTo(row.get("employment_type"));
        assertThat(body.get("status").asString()).isEqualTo(row.get("status"));
        assertThat(body.get("hireDate").asString()).isEqualTo(row.get("hire_date").toString());
    }

    @Test
    void currentPayIsTheOpenRecordInLocalCurrencyAndUsdAtTheCurrentRate() throws Exception {
        Map<String, Object> pay = jdbc.queryForMap(
                "SELECT e.id, c.amount, c.currency FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                        + "AND c.effective_end IS NULL WHERE e.employee_id = 'EMP-000001'");
        BigDecimal amount = new BigDecimal(pay.get("amount").toString());
        String currency = (String) pay.get("currency");
        BigDecimal usd = amount.multiply(rates.snapshot().rateToUsd(Currency.getInstance(currency))).setScale(2, RoundingMode.HALF_UP);

        JsonNode body = ok("/api/v1/employees/" + pay.get("id"));

        assertThat(body.get("salary").get("currency").asString()).isEqualTo(currency);
        assertThat(new BigDecimal(body.get("salary").get("amount").asString())).isEqualByComparingTo(amount.setScale(2, RoundingMode.HALF_UP));
        assertThat(body.get("salaryUsd").get("currency").asString()).isEqualTo("USD");
        assertThat(new BigDecimal(body.get("salaryUsd").get("amount").asString())).isEqualByComparingTo(usd);
    }

    @Test
    void anUnknownOrMalformedIdIsNotFound() throws Exception {
        assertProblem(get("/api/v1/employees/" + UUID.randomUUID()), 404, "EMPLOYEE_NOT_FOUND");
        assertProblem(get("/api/v1/employees/abc"), 404, "EMPLOYEE_NOT_FOUND");
        assertProblem(get("/api/v1/employees/12345"), 404, "EMPLOYEE_NOT_FOUND");
    }

    @Test
    void thefiltersEndpointIsStillReachableNextToTheIdRoute() throws Exception {
        assertThat(ok("/api/v1/employees/filters").get("countries")).hasSize(8);
    }

    // ---- salary history --------------------------------------------------------------------------

    @Test
    void historyListsEveryRecordNewestFirstAsAGaplessChainWithOneCurrentRecord() throws Exception {
        String id = jdbc.queryForObject(
                "SELECT employee_id FROM compensation_record GROUP BY employee_id HAVING COUNT(*) >= 3 ORDER BY employee_id LIMIT 1", String.class);
        int expectedCount = jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record WHERE employee_id = ?", Integer.class, id);

        JsonNode history = ok("/api/v1/employees/" + id + "/salary-history");

        assertThat(history).hasSize(expectedCount);
        assertThat(history.get(0).get("effectiveEnd").isNull()).as("the newest record is the open one").isTrue();
        long current = 0;
        for (int i = 0; i < history.size(); i++) {
            JsonNode record = history.get(i);
            if (record.get("current").asBoolean()) {
                current++;
            }
            if (i + 1 < history.size()) {
                LocalDate newerStart = LocalDate.parse(record.get("effectiveStart").asString());
                LocalDate olderEnd = LocalDate.parse(history.get(i + 1).get("effectiveEnd").asString());
                assertThat(olderEnd).as("no gap between records").isEqualTo(newerStart.minusDays(1));
                assertThat(LocalDate.parse(history.get(i + 1).get("effectiveStart").asString())).isBefore(newerStart);
                assertThat(record.get("current").asBoolean()).isEqualTo(i == 0);
            }
        }
        assertThat(current).isEqualTo(1);
    }

    @Test
    void historyShowsEachAmountInLocalCurrencyAndInUsdAtCurrentRates() throws Exception {
        String id = jdbc.queryForObject("SELECT employee_id FROM compensation_record GROUP BY employee_id HAVING COUNT(*) >= 2 LIMIT 1", String.class);

        JsonNode history = ok("/api/v1/employees/" + id + "/salary-history");

        for (JsonNode record : history) {
            Currency currency = Currency.getInstance(record.get("amount").get("currency").asString());
            BigDecimal amount = new BigDecimal(record.get("amount").get("amount").asString());
            BigDecimal usd = amount.multiply(rates.snapshot().rateToUsd(currency)).setScale(2, RoundingMode.HALF_UP);
            assertThat(record.get("amountUsd").get("currency").asString()).isEqualTo("USD");
            assertThat(new BigDecimal(record.get("amountUsd").get("amount").asString()).subtract(usd).abs())
                    .as("USD of " + amount + " " + currency)
                    .isLessThanOrEqualTo(new BigDecimal("0.01"));
        }
    }

    @Test
    void anEmployeeWithOneRecordHasAHistoryOfOneCurrentRecord() throws Exception {
        String id = jdbc.queryForObject("SELECT employee_id FROM compensation_record GROUP BY employee_id HAVING COUNT(*) = 1 LIMIT 1", String.class);

        JsonNode history = ok("/api/v1/employees/" + id + "/salary-history");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("current").asBoolean()).isTrue();
        assertThat(history.get(0).get("effectiveEnd").isNull()).isTrue();
    }

    @Test
    void historyForAnUnknownOrMalformedIdIsNotFound() throws Exception {
        assertProblem(get("/api/v1/employees/" + UUID.randomUUID() + "/salary-history"), 404, "SALARY_HISTORY_NOT_FOUND");
        assertProblem(get("/api/v1/employees/abc/salary-history"), 404, "SALARY_HISTORY_NOT_FOUND");
    }

    // ---- security --------------------------------------------------------------------------------

    @Test
    void bothEndpointsRequireASignedInUser() throws Exception {
        String id = jdbc.queryForObject("SELECT id FROM employee LIMIT 1", String.class);
        for (String path : List.of("/api/v1/employees/" + id, "/api/v1/employees/" + id + "/salary-history")) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertProblem(response, 401, "UNAUTHENTICATED");
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private JsonNode ok(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as(path + " -> " + response.body()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo(code);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
