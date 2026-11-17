package com.acme.salary.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.audit.api.AuditEntityType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditAllowlistTest {

    @Test
    void keepsOnlyTheFieldsAnEntityTypeMayAudit() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("jobTitle", "Engineer");
        raw.put("passwordHash", "secret");
        raw.put("version", 3);

        Map<String, Object> filtered = AuditAllowlist.filter(AuditEntityType.EMPLOYEE, raw);

        assertThat(filtered).containsOnlyKeys("jobTitle");
    }

    @Test
    void keepsMoneyAsAnAmountAndCurrencyObject() {
        Map<String, Object> amount = Map.of("amount", "100.00", "currency", "AUD");

        Map<String, Object> filtered =
                AuditAllowlist.filter(AuditEntityType.COMPENSATION_RECORD, Map.of("amount", amount));

        assertThat(filtered).containsEntry("amount", amount);
    }

    @Test
    void eachEntityTypeHasItsOwnAllowlist() {
        assertThat(AuditAllowlist.fieldsOf(AuditEntityType.EXCHANGE_RATE))
                .contains("currency", "rate")
                .doesNotContain("amount");
        assertThat(AuditAllowlist.fieldsOf(AuditEntityType.COMPENSATION_RECORD))
                .contains("amount", "effectiveStart", "effectiveEnd");
        assertThat(AuditAllowlist.fieldsOf(AuditEntityType.EMPLOYEE))
                .contains("firstName", "workEmail", "salary", "country");
    }

    @Test
    void anAbsentPayloadStaysAbsent() {
        assertThat(AuditAllowlist.filter(AuditEntityType.EMPLOYEE, null)).isNull();
    }
}
