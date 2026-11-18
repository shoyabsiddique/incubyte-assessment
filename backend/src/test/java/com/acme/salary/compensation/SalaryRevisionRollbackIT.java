package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** AD-5: when the audit write fails, neither the closed record nor the new one may survive. */
class SalaryRevisionRollbackIT extends AbstractMariaDbIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @MockitoBean
    private AuditTrail audit;

    private ApiClient api;
    private final String email = UUID.randomUUID() + "@acme.example";

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM compensation_record WHERE employee_id IN (SELECT id FROM employee WHERE work_email = ?)", email);
        jdbc.update("DELETE FROM employee WHERE work_email = ?", email);
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    @Test
    void aFailingAuditWriteLeavesThePreviousRecordOpenAndCreatesNothing() throws Exception {
        api = new ApiClient(port, users, encoder);
        String create = "{\"firstName\":\"Ada\",\"lastName\":\"L\",\"workEmail\":\"" + email + "\",\"jobTitle\":\"Engineer\","
                + "\"department\":\"Engineering\",\"country\":\"AUSTRALIA\",\"city\":\"Sydney\","
                + "\"employmentType\":\"FULL_TIME\",\"hireDate\":\"2024-03-01\",\"status\":\"ACTIVE\","
                + "\"salaryAmount\":\"100000\",\"salaryCurrency\":\"AUD\",\"salaryEffectiveStart\":\"2024-03-01\"}";
        String id = api.post("/api/v1/employees", create).body().get("id").asString();
        doThrow(new IllegalStateException("audit down")).when(audit).record(any(AuditEvent.class));

        int status = api.post(
                        "/api/v1/employees/" + id + "/salary-revisions",
                        "{\"amount\":\"120000\",\"effectiveStart\":\"2025-01-01\"}")
                .status();

        assertThat(status).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record WHERE employee_id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT effective_end FROM compensation_record WHERE employee_id = ?", Object.class, id))
                .isNull();
    }
}
