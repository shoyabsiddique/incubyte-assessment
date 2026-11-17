package com.acme.salary.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditOperation;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.audit.persistence.AuditEntryRepository;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AuditTrailIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private AuditTrail audit;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private AppUserRepository users;

    private UUID userId;
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void createUser() {
        userId = UUID.randomUUID();
        users.save(new AppUserEntity(userId, "audit-" + userId + "@acme.example", "x", "Audit Tester"));
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", userId.toString());
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId.toString());
    }

    private AuditEvent event() {
        return new AuditEvent(
                userId,
                "audit@acme.example",
                AuditEntityType.COMPENSATION_RECORD,
                UUID.randomUUID(),
                employeeId,
                AuditOperation.REVISE,
                Map.of("amount", Map.of("amount", "100.00", "currency", "AUD"), "password", "nope"),
                Map.of("amount", Map.of("amount", "120.00", "currency", "AUD")));
    }

    private int entries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_entry WHERE user_id = ?", Integer.class, userId.toString());
    }

    @Test
    void storesOneEntryWithTheClockTimeAndOnlyAllowlistedFields() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> audit.record(event()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM audit_entry WHERE user_id = ?", userId.toString());
        assertThat(entries()).isEqualTo(1);
        assertThat(row.get("entity_type")).isEqualTo("COMPENSATION_RECORD");
        assertThat(row.get("operation")).isEqualTo("REVISE");
        assertThat(row.get("subject_employee_id")).isEqualTo(employeeId.toString());
        assertThat(row.get("user_email")).isEqualTo("audit@acme.example");
        assertThat(row.get("before_json").toString())
                .contains("\"amount\":\"100.00\"")
                .contains("AUD")
                .doesNotContain("password");
        assertThat(row.get("after_json").toString()).contains("\"amount\":\"120.00\"");
        Instant at = ((java.sql.Timestamp) row.get("created_at")).toInstant();
        assertThat(Duration.between(at, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void failsInsteadOfOpeningItsOwnTransaction() {
        assertThatThrownBy(() -> audit.record(event())).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(entries()).isZero();
    }

    @Test
    void isRolledBackWithTheCallersTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.record(event());
            status.setRollbackOnly();
        });

        assertThat(entries()).isZero();
    }

    @Test
    void theRepositoryOffersNoUpdateOrDelete() {
        for (Method method : AuditEntryRepository.class.getMethods()) {
            assertThat(method.getName()).doesNotStartWith("delete").doesNotStartWith("update").doesNotContain("Delete");
        }
    }
}
