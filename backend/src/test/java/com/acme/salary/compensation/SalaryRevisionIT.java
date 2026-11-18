package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SalaryRevisionIT extends AbstractMariaDbIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private TransactionTemplate transaction;

    private ApiClient api;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private String id;

    @BeforeEach
    void createAnEmployeeOnAudSalary() throws Exception {
        api = new ApiClient(port, users, encoder);
        String create = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"workEmail\":\"" + tag + ".ada@acme.example\","
                + "\"jobTitle\":\"Engineer\",\"department\":\"Engineering\",\"country\":\"AUSTRALIA\",\"city\":\"Sydney\","
                + "\"employmentType\":\"FULL_TIME\",\"hireDate\":\"2024-03-01\",\"status\":\"ACTIVE\","
                + "\"salaryAmount\":\"100000\",\"salaryCurrency\":\"AUD\",\"salaryEffectiveStart\":\"2024-03-01\"}";
        id = api.post("/api/v1/employees", create).body().get("id").asString();
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        jdbc.update("DELETE FROM compensation_record WHERE employee_id IN (SELECT id FROM employee WHERE work_email LIKE ?)", tag + "%");
        jdbc.update("DELETE FROM employee WHERE work_email LIKE ?", tag + "%");
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    private Reply revise(String amount, String start) throws Exception {
        return api.post("/api/v1/employees/" + id + "/salary-revisions", body(amount, start));
    }

    private static String body(String amount, String start) {
        return "{\"amount\":" + (amount == null ? "null" : "\"" + amount + "\"") + ",\"effectiveStart\":"
                + (start == null ? "null" : "\"" + start + "\"") + "}";
    }

    private List<Map<String, Object>> reviseEntries() {
        return jdbc.queryForList(
                "SELECT * FROM audit_entry WHERE user_id = ? AND operation = 'REVISE'", api.userId().toString());
    }

    private int recordCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record WHERE employee_id = ?", Integer.class, id);
    }

    @Test
    void aRevisionClosesThePreviousRecordOpensANewOneAndWritesOneAuditEntry() throws Exception {
        Reply reply = revise("120000.50", "2025-01-01");

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.body().get("amount").get("amount").asString()).isEqualTo("120000.50");
        assertThat(reply.body().get("amount").get("currency").asString()).isEqualTo("AUD");
        assertThat(reply.body().get("effectiveStart").asString()).isEqualTo("2025-01-01");
        assertThat(reply.body().get("effectiveEnd").isNull()).isTrue();
        assertThat(reply.body().get("current").asBoolean()).isTrue();

        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM compensation_record WHERE employee_id = ? ORDER BY effective_start", id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("effective_end").toString()).isEqualTo("2024-12-31");
        assertThat(rows.get(1).get("effective_end")).isNull();

        List<Map<String, Object>> entries = reviseEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("entity_type")).isEqualTo("COMPENSATION_RECORD");
        assertThat(entries.get(0).get("subject_employee_id")).isEqualTo(id);
        JsonNode before = JSON.readTree(entries.get(0).get("before_json").toString());
        JsonNode after = JSON.readTree(entries.get(0).get("after_json").toString());
        assertThat(before.get("amount").get("amount").asString()).isEqualTo("100000.00");
        assertThat(before.get("amount").get("currency").asString()).isEqualTo("AUD");
        assertThat(after.get("amount").get("amount").asString()).isEqualTo("120000.50");
        assertThat(after.get("effectiveStart").asString()).isEqualTo("2025-01-01");
    }

    @Test
    void theHistoryAndTheEmployeeShowTheNewSalaryAfterwards() throws Exception {
        revise("120000", "2025-01-01");

        JsonNode history = api.get("/api/v1/employees/" + id + "/salary-history").body();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("current").asBoolean()).isTrue();
        assertThat(history.get(1).get("current").asBoolean()).isFalse();
        assertThat(history.get(1).get("effectiveEnd").asString()).isEqualTo("2024-12-31");

        JsonNode employee = api.get("/api/v1/employees/" + id).body();
        assertThat(employee.get("salary").get("amount").asString()).isEqualTo("120000.00");
    }

    @Test
    void aFutureDatedRevisionLeavesThePresentSalaryCurrentUntilItsStartArrives() throws Exception {
        Reply reply = revise("150000", "2099-07-01");

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.body().get("current").asBoolean()).isFalse();
        JsonNode history = api.get("/api/v1/employees/" + id + "/salary-history").body();
        assertThat(history.get(0).get("current").asBoolean()).isFalse();
        assertThat(history.get(1).get("current").asBoolean()).isTrue();
        assertThat(history.get(1).get("effectiveEnd").asString()).isEqualTo("2099-06-30");
    }

    @Test
    void revisionsChainWithoutGaps() throws Exception {
        assertThat(revise("110000", "2024-09-01").status()).isEqualTo(201);
        assertThat(revise("120000", "2025-03-15").status()).isEqualTo(201);

        JsonNode history = api.get("/api/v1/employees/" + id + "/salary-history").body();
        assertThat(history).hasSize(3);
        assertThat(history.get(2).get("effectiveEnd").asString()).isEqualTo("2024-08-31");
        assertThat(history.get(1).get("effectiveEnd").asString()).isEqualTo("2025-03-14");
        assertThat(history.get(0).get("effectiveEnd").isNull()).isTrue();
        assertThat(reviseEntries()).hasSize(2);
    }

    @Test
    void aDateThatIsNotAfterTheLatestStartIsRejectedAndChangesNothing() throws Exception {
        for (String date : new String[] {"2024-03-01", "2023-12-31"}) {
            Reply reply = revise("120000", date);

            assertThat(reply.status()).as(date).isEqualTo(400);
            assertThat(reply.body().get("code").asString()).as(date).isEqualTo("REVISION_DATE_INVALID");
            assertThat(reply.body().get("errors").get(0).get("field").asString()).isEqualTo("effectiveStart");
        }
        assertThat(recordCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT effective_end FROM compensation_record WHERE employee_id = ?", Object.class, id)).isNull();
        assertThat(reviseEntries()).isEmpty();
    }

    @Test
    void invalidAmountsAndDatesAreRejectedAndChangeNothing() throws Exception {
        for (String amount : new String[] {"0", "-1", "abc", "", "100.123"}) {
            Reply reply = revise(amount, "2025-01-01");
            assertThat(reply.status()).as(amount).isEqualTo(400);
            assertThat(reply.body().get("code").asString()).isEqualTo("VALIDATION_FAILED");
            assertThat(reply.body().get("errors").get(0).get("field").asString()).isEqualTo("amount");
        }
        assertThat(revise(null, "2025-01-01").status()).isEqualTo(400);
        assertThat(revise("120000", null).status()).isEqualTo(400);
        assertThat(revise("120000", "not-a-date").status()).isEqualTo(400);
        assertThat(api.post("/api/v1/employees/" + id + "/salary-revisions", "not json").status()).isEqualTo(400);
        assertThat(recordCount()).isEqualTo(1);
        assertThat(reviseEntries()).isEmpty();
    }

    @Test
    void anUnknownEmployeeIsNotFound() throws Exception {
        for (String unknown : new String[] {UUID.randomUUID().toString(), "not-a-uuid"}) {
            Reply reply = api.post("/api/v1/employees/" + unknown + "/salary-revisions", body("120000", "2025-01-01"));

            assertThat(reply.status()).as(unknown).isEqualTo(404);
            assertThat(reply.body().get("code").asString()).isEqualTo("EMPLOYEE_NOT_FOUND");
        }
        assertThat(reviseEntries()).isEmpty();
    }

    @Test
    void aRequestWithoutATokenIsRefused() throws Exception {
        assertThat(api.withoutToken("POST", "/api/v1/employees/" + id + "/salary-revisions", body("1", "2025-01-01")).status())
                .isEqualTo(401);
        assertThat(recordCount()).isEqualTo(1);
    }

    @Test
    void whenAnotherRevisionWinsWhileThisOneWaitsItGets409RevisionOverlapAndLeavesNoTraceOfItself() throws Exception {
        CompletableFuture<Reply> loser = new CompletableFuture<>();
        transaction.executeWithoutResult(status -> {
            // The winner, written by hand: it has closed the open record and added its own, but not yet committed.
            jdbc.update(
                    "UPDATE compensation_record SET effective_end = '2024-12-31', version = version + 1 "
                            + "WHERE employee_id = ? AND effective_end IS NULL",
                    id);
            jdbc.update(
                    "INSERT INTO compensation_record (id, employee_id, amount, currency, effective_start, version) "
                            + "VALUES (?, ?, 111111, 'AUD', '2025-01-01', 0)",
                    UUID.randomUUID().toString(), id);
            CompletableFuture.runAsync(() -> {
                try {
                    loser.complete(revise("122222", "2025-02-01"));
                } catch (Exception failed) {
                    loser.completeExceptionally(failed);
                }
            });
            waitUntilARequestIsWaitingForTheRowLock(loser);
        });

        Reply reply = loser.get(20, TimeUnit.SECONDS);

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.body().get("code").asString()).isEqualTo("REVISION_OVERLAP");
        assertThat(recordCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM compensation_record WHERE employee_id = ? AND effective_end IS NULL",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(reviseEntries()).isEmpty();
    }

    @Test
    void twoRevisionsSubmittedTogetherLeaveExactlyOneSuccessAndOneOpenRecord() throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        List<CompletableFuture<Reply>> replies = List.of("120000", "130000").stream()
                .map(amount -> CompletableFuture.supplyAsync(() -> {
                    try {
                        together.await(10, TimeUnit.SECONDS);
                        return revise(amount, "2025-01-01");
                    } catch (Exception failed) {
                        throw new IllegalStateException(failed);
                    }
                }))
                .toList();

        List<Reply> results = replies.stream().map(CompletableFuture::join).toList();

        assertThat(results.stream().filter(r -> r.status() == 201)).hasSize(1);
        Reply loser = results.stream().filter(r -> r.status() != 201).findFirst().orElseThrow();
        assertThat(loser.status()).isIn(400, 409);
        assertThat(loser.body().get("code").asString()).isIn("REVISION_OVERLAP", "REVISION_DATE_INVALID");
        assertThat(recordCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM compensation_record WHERE employee_id = ? AND effective_end IS NULL",
                        Integer.class,
                        id))
                .isEqualTo(1);
        assertThat(reviseEntries()).hasSize(1);
    }

    /**
     * Gives the second request time to read the old state and queue behind the first one's row lock. A request
     * that had already finished would mean it was not blocked at all, which would make this test prove nothing.
     */
    private static void waitUntilARequestIsWaitingForTheRowLock(CompletableFuture<Reply> request) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        assertThat(request).as("the second revision should be waiting for the row lock").isNotDone();
    }
}
