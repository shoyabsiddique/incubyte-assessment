package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** AD-5: when the audit write fails, the business write it belongs to must not survive. */
class EmployeeCreateRollbackIT extends AbstractMariaDbIntegrationTest {

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

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    @Test
    void aFailingAuditWriteRollsBackTheWholeCreate() throws Exception {
        api = new ApiClient(port, users, encoder);
        doThrow(new IllegalStateException("audit down")).when(audit).record(any(AuditEvent.class));
        int employees = jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class);
        int records = jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record", Integer.class);
        String email = UUID.randomUUID() + "@acme.example";

        Reply reply = api.post(
                "/api/v1/employees",
                "{\"firstName\":\"Ada\",\"lastName\":\"L\",\"workEmail\":\"" + email + "\",\"jobTitle\":\"Engineer\","
                        + "\"department\":\"Engineering\",\"country\":\"AUSTRALIA\",\"city\":\"Sydney\","
                        + "\"employmentType\":\"FULL_TIME\",\"hireDate\":\"2024-03-01\",\"status\":\"ACTIVE\","
                        + "\"salaryAmount\":\"100000\",\"salaryCurrency\":\"AUD\",\"salaryEffectiveStart\":\"2024-03-01\"}");

        assertThat(reply.status()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class)).isEqualTo(employees);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record", Integer.class)).isEqualTo(records);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE work_email = ?", Integer.class, email)).isZero();
    }
}
