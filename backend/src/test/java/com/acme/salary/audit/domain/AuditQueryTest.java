package com.acme.salary.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.shared.domain.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditQueryTest {

    @Test
    void defaultsToTheFirstPageOfTwentyWithNoFilters() {
        AuditQuery query = AuditQuery.of(null, null, null, null, null, null);

        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.userId()).isEmpty();
        assertThat(query.entityType()).isEmpty();
        assertThat(query.from()).isEmpty();
        assertThat(query.toExclusive()).isEmpty();
    }

    @Test
    void parsesEveryFilter() {
        UUID user = UUID.randomUUID();

        AuditQuery query = AuditQuery.of("2", "50", user.toString(), "EXCHANGE_RATE", "2026-03-01", "2026-03-31");

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
        assertThat(query.userId()).contains(user);
        assertThat(query.entityType()).contains(AuditEntityType.EXCHANGE_RATE);
        assertThat(query.from()).contains(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(query.toExclusive()).contains(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void theEndDateIsInclusiveSoItsWholeDayIsIncluded() {
        AuditQuery query = AuditQuery.of(null, null, null, null, "2026-03-31", "2026-03-31");

        assertThat(query.from()).contains(Instant.parse("2026-03-31T00:00:00Z"));
        assertThat(query.toExclusive()).contains(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void canBeNarrowedToOneEmployee() {
        UUID employee = UUID.randomUUID();

        assertThat(AuditQuery.of(null, null, null, null, null, null).forEmployee(employee).subjectEmployeeId())
                .contains(employee);
    }

    @Test
    void rejectsValuesItCanNotUnderstand() {
        assertCode(() -> AuditQuery.of("-1", null, null, null, null, null), "INVALID_PAGE");
        assertCode(() -> AuditQuery.of("x", null, null, null, null, null), "INVALID_PAGE");
        assertCode(() -> AuditQuery.of(null, "0", null, null, null, null), "INVALID_PAGE");
        assertCode(() -> AuditQuery.of("2147483647", null, null, null, null, null), "INVALID_PAGE");
        assertCode(() -> AuditQuery.of(null, "101", null, null, null, null), "PAGE_SIZE_TOO_LARGE");
        assertCode(() -> AuditQuery.of(null, null, "not-a-uuid", null, null, null), "INVALID_FILTER");
        assertCode(() -> AuditQuery.of(null, null, null, "SALARY", null, null), "INVALID_FILTER");
        assertCode(() -> AuditQuery.of(null, null, null, null, "31/03/2026", null), "INVALID_FILTER");
        assertCode(() -> AuditQuery.of(null, null, null, null, "2026-04-01", "2026-03-01"), "INVALID_FILTER");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }
}
