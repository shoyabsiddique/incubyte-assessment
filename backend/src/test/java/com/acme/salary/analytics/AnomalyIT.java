package com.acme.salary.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.math.BigDecimal;
import java.util.List;
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

/** Potential anomalies over real HTTP against the seeded 10,000 employees, which include deliberate outliers. */
class AnomalyIT extends AbstractMariaDbIntegrationTest {

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
        Reply reply = api.get("/api/v1/analytics/anomalies" + query);
        assertThat(reply.status()).as(query).isEqualTo(200);
        return reply.body();
    }

    @Test
    void returnsTheLabelTheEnvelopeAndEveryDeliberateOutlier() throws Exception {
        JsonNode body = ok("?size=100");

        assertThat(body.get("label").asString()).isEqualTo("Potential anomalies for HR review");
        assertThat(body.get("totalElements").asLong()).isGreaterThanOrEqualTo(16);
        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(100);
        long outliers = 0;
        for (JsonNode item : body.get("items")) {
            double deviation = Math.abs(Double.parseDouble(item.get("deviationPercent").asString()));
            assertThat(deviation).isGreaterThan(20.0);
            assertThat(item.get("peerGroupSize").asInt()).isGreaterThanOrEqualTo(5);
            if (deviation > 20) {
                outliers++;
            }
        }
        assertThat(outliers).isGreaterThanOrEqualTo(16);
    }

    @Test
    void eachItemCarriesTheEmployeeTheirPeerGroupAndTheReasoning() throws Exception {
        JsonNode item = ok("?size=1").get("items").get(0);

        for (String field : new String[] {"id", "employeeId", "name", "country", "countryName", "department", "jobTitle", "deviationPercent", "direction"}) {
            assertThat(item.get(field).asString()).as(field).isNotBlank();
        }
        assertThat(item.get("salary").get("currency").asString()).isNotBlank();
        assertThat(item.get("salaryUsd").get("currency").asString()).isEqualTo("USD");
        assertThat(item.get("peerMedianUsd").get("currency").asString()).isEqualTo("USD");
        BigDecimal usd = new BigDecimal(item.get("salaryUsd").get("amount").asString());
        BigDecimal median = new BigDecimal(item.get("peerMedianUsd").get("amount").asString());
        BigDecimal shown = new BigDecimal(item.get("deviationPercent").asString());
        BigDecimal computed = usd.subtract(median).multiply(BigDecimal.valueOf(100)).divide(median, 2, java.math.RoundingMode.HALF_UP);
        assertThat(shown.subtract(computed).abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
    }

    @Test
    void theMedianOfTheGroupIsTheMedianOfWhatIsShownForThatGroup() throws Exception {
        JsonNode item = ok("?size=1").get("items").get(0);
        String employeeId = item.get("employeeId").asString();
        List<BigDecimal> peers = jdbc.query(
                "SELECT c.amount * r.rate_to_usd FROM employee e JOIN employee me ON me.employee_id = ? "
                        + "AND e.country = me.country AND LOWER(TRIM(e.department)) = LOWER(TRIM(me.department)) "
                        + "AND LOWER(TRIM(e.job_title)) = LOWER(TRIM(me.job_title)) "
                        + "JOIN compensation_record c ON c.employee_id = e.id AND c.effective_end IS NULL "
                        + "JOIN exchange_rate r ON r.currency = c.currency WHERE e.status = 'ACTIVE' "
                        + "ORDER BY c.amount * r.rate_to_usd",
                (rs, n) -> rs.getBigDecimal(1),
                employeeId);

        assertThat(peers.size()).isEqualTo(item.get("peerGroupSize").asInt());
        BigDecimal median = peers.size() % 2 == 1
                ? peers.get(peers.size() / 2)
                : peers.get(peers.size() / 2 - 1).add(peers.get(peers.size() / 2)).divide(BigDecimal.valueOf(2));
        BigDecimal shown = new BigDecimal(item.get("peerMedianUsd").get("amount").asString());
        assertThat(shown.subtract(median).abs()).isLessThanOrEqualTo(new BigDecimal("0.01"));
    }

    @Test
    void filtersNarrowTheList() throws Exception {
        JsonNode all = ok("?size=100");
        JsonNode above = ok("?size=100&direction=above");
        JsonNode below = ok("?size=100&direction=below");
        assertThat(above.get("totalElements").asLong() + below.get("totalElements").asLong())
                .isEqualTo(all.get("totalElements").asLong());
        above.get("items").forEach(i -> assertThat(i.get("direction").asString()).isEqualTo("ABOVE"));

        String country = all.get("items").get(0).get("country").asString();
        String department = all.get("items").get(0).get("department").asString();
        JsonNode scoped = ok("?size=100&country=" + country + "&department=" + department.replace(" ", "%20").replace("&", "%26"));
        assertThat(scoped.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        scoped.get("items").forEach(i -> {
            assertThat(i.get("country").asString()).isEqualTo(country);
            assertThat(i.get("department").asString()).isEqualTo(department);
        });
    }

    @Test
    void pagesAndSortsDeterministically() throws Exception {
        JsonNode first = ok("?size=5&page=0");
        JsonNode second = ok("?size=5&page=1");
        assertThat(first.get("items")).hasSize(5);
        assertThat(first.get("totalPages").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(second.get("items").get(0).get("employeeId").asString())
                .isNotEqualTo(first.get("items").get(0).get("employeeId").asString());

        JsonNode byName = ok("?size=100&sort=name,asc");
        String previous = "";
        for (JsonNode item : byName.get("items")) {
            String name = item.get("name").asString().toLowerCase();
            assertThat(name.compareTo(previous)).isGreaterThanOrEqualTo(0);
            previous = name;
        }
        assertThat(ok("?size=3").get("items").get(0).get("employeeId"))
                .isEqualTo(ok("?size=3").get("items").get(0).get("employeeId"));
    }

    @Test
    void invalidInputIsRejectedAndTokensAreRequired() throws Exception {
        for (String bad : new String[] {"?direction=sideways", "?country=ATLANTIS", "?department=Nope", "?sort=salaryx", "?sort=name,up", "?page=-1", "?page=x", "?size=0", "?size=101"}) {
            assertThat(api.get("/api/v1/analytics/anomalies" + bad).status()).as(bad).isEqualTo(400);
        }
        assertThat(api.get("/api/v1/analytics/anomalies?size=101").body().get("code").asString()).isEqualTo("PAGE_SIZE_TOO_LARGE");
        assertThat(api.withoutToken("GET", "/api/v1/analytics/anomalies", null).status()).isEqualTo(401);
    }

    @Test
    void respondsWithinTheTargetOnTenThousandEmployees() throws Exception {
        ok(""); // warm up
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long start = System.nanoTime();
            ok("");
            best = Math.min(best, (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("ANALYTICS-PERF anomalies=" + best + "ms");
        assertThat(best).as("anomalies best of five, ms").isLessThan(500);
    }
}
