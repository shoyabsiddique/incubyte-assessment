package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExchangeRateEditIT extends AbstractMariaDbIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private ApiClient api;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private List<Map<String, Object>> originalRates;

    @BeforeEach
    void rememberTheRates() throws Exception {
        api = new ApiClient(port, users, encoder);
        originalRates = jdbc.queryForList("SELECT currency, rate_to_usd, version FROM exchange_rate");
    }

    @AfterEach
    void putTheRatesBack() {
        for (Map<String, Object> row : originalRates) {
            jdbc.update(
                    "UPDATE exchange_rate SET rate_to_usd = ?, version = ? WHERE currency = ?",
                    row.get("rate_to_usd"), row.get("version"), row.get("currency"));
        }
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        jdbc.update("DELETE FROM compensation_record WHERE employee_id IN (SELECT id FROM employee WHERE work_email LIKE ?)", tag + "%");
        jdbc.update("DELETE FROM employee WHERE work_email LIKE ?", tag + "%");
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    private JsonNode rate(String currency) throws Exception {
        for (JsonNode entry : api.get("/api/v1/exchange-rates").body()) {
            if (entry.get("currency").asString().equals(currency)) {
                return entry;
            }
        }
        throw new AssertionError("no rate for " + currency);
    }

    private Reply edit(String currency, String rate, Long version) throws Exception {
        String body = "{\"rate\":" + (rate == null ? "null" : "\"" + rate + "\"") + ",\"version\":" + version + "}";
        return api.put("/api/v1/exchange-rates/" + currency, body);
    }

    private List<Map<String, Object>> rateEntries() {
        return jdbc.queryForList("SELECT * FROM audit_entry WHERE user_id = ? AND entity_type = 'EXCHANGE_RATE'", api.userId().toString());
    }

    @Test
    void listsOneRatePerSupportedCurrencyWithUsdFixedAtOne() throws Exception {
        Reply reply = api.get("/api/v1/exchange-rates");

        assertThat(reply.status()).isEqualTo(200);
        List<String> currencies = new ArrayList<>();
        reply.body().forEach(entry -> currencies.add(entry.get("currency").asString()));
        assertThat(currencies).containsExactlyInAnyOrder("USD", "INR", "GBP", "EUR", "SGD", "AED", "CAD", "AUD");
        JsonNode usd = rate("USD");
        assertThat(usd.get("rate").asString()).isEqualTo("1");
        assertThat(usd.get("editable").asBoolean()).isFalse();
        JsonNode inr = rate("INR");
        assertThat(inr.get("rate").asString()).isEqualTo("0.012");
        assertThat(inr.get("editable").asBoolean()).isTrue();
        assertThat(inr.get("version").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void anEditSavesTheRateAndWritesOneAuditEntryWithoutASubjectEmployee() throws Exception {
        long version = rate("INR").get("version").asLong();

        Reply reply = edit("INR", "0.0125", version);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("currency").asString()).isEqualTo("INR");
        assertThat(reply.body().get("rate").asString()).isEqualTo("0.0125");
        assertThat(reply.body().get("version").asLong()).isEqualTo(version + 1);
        assertThat(jdbc.queryForObject("SELECT rate_to_usd FROM exchange_rate WHERE currency = 'INR'", BigDecimal.class))
                .isEqualByComparingTo("0.0125");

        List<Map<String, Object>> entries = rateEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("operation")).isEqualTo("UPDATE");
        assertThat(entries.get(0).get("subject_employee_id")).isNull();
        JsonNode before = JSON.readTree(entries.get(0).get("before_json").toString());
        JsonNode after = JSON.readTree(entries.get(0).get("after_json").toString());
        assertThat(before.get("rate").asString()).isEqualTo("0.012");
        assertThat(after.get("rate").asString()).isEqualTo("0.0125");
        assertThat(after.get("currency").asString()).isEqualTo("INR");
    }

    @Test
    void savingTheSameRateAgainChangesNothingAndWritesNoAuditEntry() throws Exception {
        long version = rate("GBP").get("version").asLong();

        Reply reply = edit("GBP", "1.27000", version);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("version").asLong()).isEqualTo(version);
        assertThat(rateEntries()).isEmpty();
    }

    @Test
    void invalidRatesAreRejectedAndChangeNothing() throws Exception {
        long version = rate("INR").get("version").asLong();
        for (String bad : new String[] {"0", "-1", "abc", "", "0.123456789", "1e3"}) {
            Reply reply = edit("INR", bad, version);
            assertThat(reply.status()).as(bad).isEqualTo(400);
            assertThat(reply.body().get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(reply.body().get("errors").get(0).get("field").asString()).isEqualTo("rate");
        }
        assertThat(edit("INR", null, version).status()).isEqualTo(400);
        assertThat(api.put("/api/v1/exchange-rates/INR", "not json").status()).isEqualTo(400);
        assertThat(rate("INR").get("rate").asString()).isEqualTo("0.012");
        assertThat(rateEntries()).isEmpty();
    }

    @Test
    void usdCanNotBeEditedAndUnknownCurrenciesAreRejected() throws Exception {
        Reply usd = edit("USD", "2", 0L);
        assertThat(usd.status()).isEqualTo(400);
        assertThat(usd.body().get("code").asString()).isEqualTo("USD_RATE_FIXED");

        Reply unknown = edit("JPY", "0.0067", 0L);
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.body().get("code").asString()).isEqualTo("UNSUPPORTED_CURRENCY");

        assertThat(rate("USD").get("rate").asString()).isEqualTo("1");
        assertThat(rateEntries()).isEmpty();
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        long version = rate("EUR").get("version").asLong();
        assertThat(edit("EUR", "1.10", version).status()).isEqualTo(200);

        Reply stale = edit("EUR", "1.20", version);

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body().get("code").asString()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(rate("EUR").get("rate").asString()).isEqualTo("1.1");
        assertThat(rateEntries()).hasSize(1);
    }

    @Test
    void aMissingVersionIsRejected() throws Exception {
        Reply reply = api.put("/api/v1/exchange-rates/INR", "{\"rate\":\"0.02\"}");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().get("errors").get(0).get("field").asString()).isEqualTo("version");
    }

    @Test
    void requestsWithoutATokenAreRefused() throws Exception {
        assertThat(api.withoutToken("GET", "/api/v1/exchange-rates", null).status()).isEqualTo(401);
        assertThat(api.withoutToken("PUT", "/api/v1/exchange-rates/INR", "{}").status()).isEqualTo(401);
    }

    @Test
    void afterARateEditEmployeesShowTheNewUsdEquivalentWithNoStoredUsdValueChanged() throws Exception {
        String create = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"workEmail\":\"" + tag + ".ada@acme.example\","
                + "\"jobTitle\":\"Engineer\",\"department\":\"Engineering\",\"country\":\"AUSTRALIA\",\"city\":\"Sydney\","
                + "\"employmentType\":\"FULL_TIME\",\"hireDate\":\"2024-03-01\",\"status\":\"ACTIVE\","
                + "\"salaryAmount\":\"100000\",\"salaryCurrency\":\"AUD\",\"salaryEffectiveStart\":\"2024-03-01\"}";
        String id = api.post("/api/v1/employees", create).body().get("id").asString();
        assertThat(api.get("/api/v1/employees/" + id).body().get("salaryUsd").get("amount").asString()).isEqualTo("65000.00");

        edit("AUD", "0.70", rate("AUD").get("version").asLong());

        assertThat(api.get("/api/v1/employees/" + id).body().get("salaryUsd").get("amount").asString()).isEqualTo("70000.00");
        assertThat(api.get("/api/v1/employees/" + id + "/salary-history").body().get(0).get("amountUsd").get("amount").asString())
                .isEqualTo("70000.00");
        assertThat(jdbc.queryForObject("SELECT amount FROM compensation_record WHERE employee_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("100000");
    }
}
