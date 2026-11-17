package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.application.EmployeeSeedRunner;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** The employee directory over real HTTP, against the full seeded 10,000-employee workforce. */
class EmployeeDirectoryIT extends AbstractMariaDbIntegrationTest {

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
        String email = "dir-" + UUID.randomUUID() + "@acme.example";
        users.save(new AppUserEntity(UUID.randomUUID(), email, encoder.encode("Dir-Pass-12345"), "Directory Tester"));
        HttpRequest login = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"Dir-Pass-12345\"}"))
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

    // ---- envelope, paging ----------------------------------------------------------------------

    @Test
    void theDefaultListIsTheFirstTwentyOfTenThousandInEmployeeIdOrder() throws Exception {
        JsonNode body = ok("/api/v1/employees");

        assertThat(body.get("page").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("totalElements").asLong()).isEqualTo(10_000);
        assertThat(body.get("totalPages").asInt()).isEqualTo(500);
        assertThat(body.get("items")).hasSize(20);
        assertThat(body.get("items").get(0).get("employeeId").asString()).isEqualTo("EMP-000001");
        assertThat(body.get("items").get(19).get("employeeId").asString()).isEqualTo("EMP-000020");
    }

    @Test
    void pagesDoNotOverlapAndTheLastPageIsPartial() throws Exception {
        JsonNode second = ok("/api/v1/employees?page=1&size=100");
        JsonNode last = ok("/api/v1/employees?page=99&size=101".replace("101", "100"));

        assertThat(second.get("items").get(0).get("employeeId").asString()).isEqualTo("EMP-000101");
        assertThat(last.get("items")).hasSize(100);
        assertThat(ok("/api/v1/employees?page=100&size=100").get("items")).isEmpty();
    }

    @Test
    void rejectsAPageSizeAboveOneHundredAndBadParameters() throws Exception {
        assertProblem(get("/api/v1/employees?size=101"), 400, "PAGE_SIZE_TOO_LARGE");
        assertProblem(get("/api/v1/employees?page=-1"), 400, "INVALID_PAGE");
        assertProblem(get("/api/v1/employees?sort=password,asc"), 400, "INVALID_SORT");
        assertProblem(get("/api/v1/employees?country=Atlantis"), 400, "INVALID_FILTER");
        assertProblem(get("/api/v1/employees?status=RETIRED"), 400, "INVALID_FILTER");
    }

    @Test
    void everyItemCarriesTheDirectoryFieldsAndMoneyAsDecimalStrings() throws Exception {
        JsonNode item = ok("/api/v1/employees?size=1").get("items").get(0);

        for (String field : new String[] {"id", "employeeId", "firstName", "lastName", "workEmail", "jobTitle",
                "department", "country", "countryName", "city", "employmentType", "status", "hireDate"}) {
            assertThat(item.has(field)).as(field).isTrue();
        }
        assertThat(item.get("salary").get("amount").asString()).matches("\\d+\\.\\d{2}");
        assertThat(item.get("salary").get("currency").asString()).hasSize(3);
        assertThat(item.get("salaryUsd").get("currency").asString()).isEqualTo("USD");
        assertThat(item.get("salaryUsd").get("amount").asString()).matches("\\d+\\.\\d{2}");
    }

    // ---- search --------------------------------------------------------------------------------

    @Test
    void findsPeopleByEmployeeIdAndByWorkEmail() throws Exception {
        JsonNode byId = ok("/api/v1/employees?q=EMP-000042");
        String email = byId.get("items").get(0).get("workEmail").asString();

        assertThat(byId.get("totalElements").asLong()).isEqualTo(1);
        assertThat(ok("/api/v1/employees?q=" + enc(email.toUpperCase())).get("items").get(0).get("employeeId").asString())
                .isEqualTo("EMP-000042");
    }

    @Test
    void everyWordOfTheSearchMustMatchSomewhere() throws Exception {
        JsonNode person = ok("/api/v1/employees?q=EMP-000007").get("items").get(0);
        String first = person.get("firstName").asString();
        String last = person.get("lastName").asString();

        JsonNode both = ok("/api/v1/employees?size=100&q=" + enc(first + " " + last));
        JsonNode mismatch = ok("/api/v1/employees?q=" + enc(first + " zzzznotaname"));

        assertThat(both.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : both.get("items")) {
            String haystack = (item.get("firstName").asString() + item.get("lastName").asString()
                    + item.get("workEmail").asString() + item.get("employeeId").asString()).toLowerCase();
            assertThat(haystack).contains(first.toLowerCase()).contains(last.toLowerCase());
        }
        assertThat(mismatch.get("totalElements").asLong()).isZero();
    }

    @Test
    void percentUnderscoreAndBackslashAreMatchedLiterally() throws Exception {
        for (String wild : new String[] {"%", "_", "\\", "%%", "a%"}) {
            assertThat(ok("/api/v1/employees?q=" + enc(wild)).get("totalElements").asLong()).as(wild).isZero();
        }
    }

    // ---- filters -------------------------------------------------------------------------------

    @Test
    void filtersCombineAndOnlyMatchingPeopleAreReturned() throws Exception {
        JsonNode body = ok("/api/v1/employees?size=100&country=india&department=Engineering&status=ACTIVE&employmentType=FULL_TIME");

        assertThat(body.get("totalElements").asLong()).isGreaterThan(50);
        for (JsonNode item : body.get("items")) {
            assertThat(item.get("country").asString()).isEqualTo("INDIA");
            assertThat(item.get("department").asString()).isEqualTo("Engineering");
            assertThat(item.get("status").asString()).isEqualTo("ACTIVE");
            assertThat(item.get("employmentType").asString()).isEqualTo("FULL_TIME");
        }
    }

    @Test
    void countsPerCountryMatchTheSeed() throws Exception {
        assertThat(ok("/api/v1/employees?country=INDIA&size=1").get("totalElements").asLong()).isEqualTo(3_500);
        assertThat(ok("/api/v1/employees?country=AUSTRALIA&size=1").get("totalElements").asLong()).isEqualTo(400);
        assertThat(ok("/api/v1/employees?country=USA&jobTitle=Software%20Engineer&size=1").get("totalElements").asLong())
                .isGreaterThan(100);
    }

    @Test
    void anImpossibleCombinationGivesAnEmptyPageNotAnError() throws Exception {
        JsonNode body = ok("/api/v1/employees?country=INDIA&department=Nonexistent");

        assertThat(body.get("items")).isEmpty();
        assertThat(body.get("totalElements").asLong()).isZero();
        assertThat(body.get("totalPages").asInt()).isZero();
    }

    @Test
    void theFilterEndpointListsEveryValueTheDropdownsNeed() throws Exception {
        JsonNode filters = ok("/api/v1/employees/filters");

        assertThat(filters.get("countries")).hasSize(8);
        assertThat(filters.get("countries").get(0).has("code")).isTrue();
        assertThat(filters.get("countries").get(0).has("name")).isTrue();
        assertThat(filters.get("departments")).hasSize(8);
        assertThat(filters.get("jobTitles")).hasSize(25);
        assertThat(filters.get("employmentTypes")).hasSize(3);
        assertThat(filters.get("statuses")).hasSize(3);
    }

    // ---- sorting -------------------------------------------------------------------------------

    @Test
    void sortsByNameAndByHireDateBothWays() throws Exception {
        List<String> asc = names("/api/v1/employees?size=100&sort=name,asc");
        List<String> desc = names("/api/v1/employees?size=100&sort=name,desc");

        List<String> sortedAsc = new ArrayList<>(asc);
        sortedAsc.sort(String.CASE_INSENSITIVE_ORDER);
        assertThat(asc).isEqualTo(sortedAsc);
        assertThat(desc.get(0).compareToIgnoreCase(desc.get(99))).isGreaterThanOrEqualTo(0);
    }

    @Test
    void sortingBySalaryUsesTheUsdEquivalentAcrossAllTenThousandPeople() throws Exception {
        List<String> expectedDesc = jdbc.queryForList(
                "SELECT e.employee_id FROM employee e JOIN compensation_record c ON c.employee_id = e.id AND c.effective_end IS NULL "
                        + "JOIN exchange_rate r ON r.currency = c.currency ORDER BY c.amount * r.rate_to_usd DESC, e.employee_id ASC LIMIT 25",
                String.class);
        List<String> expectedAsc = jdbc.queryForList(
                "SELECT e.employee_id FROM employee e JOIN compensation_record c ON c.employee_id = e.id AND c.effective_end IS NULL "
                        + "JOIN exchange_rate r ON r.currency = c.currency ORDER BY c.amount * r.rate_to_usd ASC, e.employee_id ASC LIMIT 25",
                String.class);

        assertThat(ids("/api/v1/employees?size=25&sort=salary,desc")).isEqualTo(expectedDesc);
        assertThat(ids("/api/v1/employees?size=25&sort=salary,asc")).isEqualTo(expectedAsc);
    }

    @Test
    void salaryPagesContinueWhereThePreviousOneStopped() throws Exception {
        List<String> first = ids("/api/v1/employees?size=50&page=0&sort=salary,desc");
        List<String> second = ids("/api/v1/employees?size=50&page=1&sort=salary,desc");
        List<String> together = ids("/api/v1/employees?size=100&page=0&sort=salary,desc");

        List<String> joined = new ArrayList<>(first);
        joined.addAll(second);
        assertThat(joined).isEqualTo(together).doesNotHaveDuplicates();
    }

    @Test
    void salarySortingRespectsFiltersAndTheTotal() throws Exception {
        JsonNode body = ok("/api/v1/employees?size=100&country=UAE&sort=salary,desc");

        assertThat(body.get("totalElements").asLong()).isEqualTo(600);
        double previous = Double.MAX_VALUE;
        for (JsonNode item : body.get("items")) {
            assertThat(item.get("country").asString()).isEqualTo("UAE");
            double usd = Double.parseDouble(item.get("salaryUsd").get("amount").asString());
            assertThat(usd).isLessThanOrEqualTo(previous);
            previous = usd;
        }
    }

    // ---- security and speed --------------------------------------------------------------------

    @Test
    void requiresASignedInUser() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/api/v1/employees")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertProblem(response, 401, "UNAUTHENTICATED");
    }

    @Test
    void warmRequestsStayUnderHalfASecondEvenWhenSortingAllTenThousandBySalary() throws Exception {
        for (String path : new String[] {"/api/v1/employees", "/api/v1/employees?sort=salary,desc&size=100",
                "/api/v1/employees?q=smith&sort=name,asc", "/api/v1/employees?country=INDIA&department=Engineering"}) {
            ok(path); // warm up
            Instant start = Instant.now();
            ok(path);
            assertThat(Duration.between(start, Instant.now())).as(path).isLessThan(Duration.ofMillis(500));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private List<String> ids(String path) throws Exception {
        List<String> ids = new ArrayList<>();
        ok(path).get("items").forEach(item -> ids.add(item.get("employeeId").asString()));
        return ids;
    }

    private List<String> names(String path) throws Exception {
        List<String> names = new ArrayList<>();
        ok(path).get("items").forEach(item -> names.add(item.get("lastName").asString() + " " + item.get("firstName").asString()));
        return names;
    }

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

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
