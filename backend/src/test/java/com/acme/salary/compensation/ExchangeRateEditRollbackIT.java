package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** AD-5: when the audit write fails, the rate change it belongs to must not survive. */
class ExchangeRateEditRollbackIT extends AbstractMariaDbIntegrationTest {

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
    void aFailingAuditWriteRollsBackTheRateChange() throws Exception {
        api = new ApiClient(port, users, encoder);
        long version = jdbc.queryForObject("SELECT version FROM exchange_rate WHERE currency = 'SGD'", Long.class);
        doThrow(new IllegalStateException("audit down")).when(audit).record(any(AuditEvent.class));

        int status = api.put("/api/v1/exchange-rates/SGD", "{\"rate\":\"0.80\",\"version\":" + version + "}").status();

        assertThat(status).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT rate_to_usd FROM exchange_rate WHERE currency = 'SGD'", BigDecimal.class))
                .isEqualByComparingTo("0.74");
        assertThat(jdbc.queryForObject("SELECT version FROM exchange_rate WHERE currency = 'SGD'", Long.class)).isEqualTo(version);
    }
}
