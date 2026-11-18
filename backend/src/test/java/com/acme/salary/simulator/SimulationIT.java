package com.acme.salary.simulator;

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

/** The increment simulator over real HTTP on the seeded 10,000 employees; it must never change data. */
class SimulationIT extends AbstractMariaDbIntegrationTest {

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

    private Reply run(String body) throws Exception {
        return api.post("/api/v1/simulations", body);
    }

    private BigDecimal money(JsonNode node) {
        assertThat(node.get("currency").asString()).isEqualTo("USD");
        return new BigDecimal(node.get("amount").asString());
    }

    private BigDecimal sqlPayroll(String where, Object... args) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Object[] all = new Object[args.length + 2];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = today;
        all[args.length + 1] = today;
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.amount * r.rate_to_usd), 0) FROM employee e JOIN compensation_record c ON c.employee_id = e.id "
                        + "JOIN exchange_rate r ON r.currency = c.currency WHERE e.status = 'ACTIVE' AND " + where
                        + " AND c.effective_start <= ? AND (c.effective_end IS NULL OR c.effective_end >= ?)",
                BigDecimal.class,
                all);
    }

    @Test
    void aTenPercentRaiseForEveryoneMatchesPlainSql() throws Exception {
        Reply reply = run("{\"percent\":10,\"scope\":\"ORGANIZATION\"}");

        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.body();
        BigDecimal current = money(body.get("currentPayroll"));
        BigDecimal expected = sqlPayroll("1 = 1");
        assertThat(current.subtract(expected).abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(money(body.get("projectedPayroll")).subtract(current.multiply(new BigDecimal("1.1"))).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(money(body.get("additionalExpenditure")).subtract(current.multiply(new BigDecimal("0.1"))).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(body.get("employees").asLong())
                .isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE status = 'ACTIVE'", Long.class));
        assertThat(body.get("scope").asString()).isEqualTo("ORGANIZATION");
        assertThat(body.get("percent").asString()).isEqualTo("10");
    }

    @Test
    void countryAndDepartmentScopesCountOnlyThoseEmployees() throws Exception {
        JsonNode india = run("{\"percent\":\"5.5\",\"scope\":\"COUNTRY\",\"value\":\"INDIA\"}").body();
        assertThat(money(india.get("currentPayroll")).subtract(sqlPayroll("e.country = ?", "INDIA")).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(india.get("value").asString()).isEqualTo("INDIA");

        String department = jdbc.queryForObject("SELECT department FROM employee WHERE status = 'ACTIVE' LIMIT 1", String.class);
        JsonNode dept = run("{\"percent\":20,\"scope\":\"DEPARTMENT\",\"value\":\"" + department + "\"}").body();
        BigDecimal current = money(dept.get("currentPayroll"));
        assertThat(current.subtract(sqlPayroll("e.department = ?", department)).abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
        assertThat(money(dept.get("projectedPayroll")).divide(current, 2, RoundingMode.HALF_UP)).isEqualByComparingTo("1.20");
    }

    @Test
    void invalidInputIsRejectedWithValidationFailed() throws Exception {
        String[] bad = {
            "{\"percent\":-1,\"scope\":\"ORGANIZATION\"}",
            "{\"percent\":101,\"scope\":\"ORGANIZATION\"}",
            "{\"percent\":\"abc\",\"scope\":\"ORGANIZATION\"}",
            "{\"scope\":\"ORGANIZATION\"}",
            "{\"percent\":10}",
            "{\"percent\":10,\"scope\":\"PLANET\"}",
            "{\"percent\":10,\"scope\":\"COUNTRY\"}",
            "{\"percent\":10,\"scope\":\"COUNTRY\",\"value\":\"ATLANTIS\"}",
            "{\"percent\":10,\"scope\":\"DEPARTMENT\"}",
            "{\"percent\":10,\"scope\":\"DEPARTMENT\",\"value\":\"Nope\"}"
        };
        for (String body : bad) {
            Reply reply = run(body);
            assertThat(reply.status()).as(body).isEqualTo(400);
            assertThat(reply.body().get("code").asString()).as(body).isEqualTo("VALIDATION_FAILED");
        }
        assertThat(run("not json").status()).isEqualTo(400);
        assertThat(api.withoutToken("POST", "/api/v1/simulations", "{}").status()).isEqualTo(401);
    }

    @Test
    void zeroAndOneHundredPercentAreAllowed() throws Exception {
        assertThat(money(run("{\"percent\":0,\"scope\":\"ORGANIZATION\"}").body().get("additionalExpenditure")))
                .isEqualByComparingTo("0");
        assertThat(run("{\"percent\":100,\"scope\":\"ORGANIZATION\"}").status()).isEqualTo(200);
    }

    @Test
    void runningSimulationsChangesNothingAndWritesNoAuditEntry() throws Exception {
        Map<String, Object> before = snapshot();

        run("{\"percent\":10,\"scope\":\"ORGANIZATION\"}");
        run("{\"percent\":25,\"scope\":\"COUNTRY\",\"value\":\"UK\"}");
        run("{\"percent\":-5,\"scope\":\"ORGANIZATION\"}");

        assertThat(snapshot()).isEqualTo(before);
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "compensation", jdbc.queryForList("SELECT id, employee_id, amount, currency, effective_start, effective_end, version FROM compensation_record ORDER BY id LIMIT 3000"),
                "compensationCount", jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record", Long.class),
                "employees", jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class),
                "employeeVersions", jdbc.queryForObject("SELECT COALESCE(SUM(version), 0) FROM employee", Long.class),
                "rates", jdbc.queryForList("SELECT currency, rate_to_usd, version FROM exchange_rate ORDER BY currency"),
                "audit", jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry", Long.class));
    }
}
