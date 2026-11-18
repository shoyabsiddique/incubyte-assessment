package com.acme.salary.compensation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class SalaryRevisionTest {

    private static final Currency AUD = Currency.getInstance("AUD");
    private static final LatestSalary LATEST = new LatestSalary(Money.of("100000.00", AUD), LocalDate.of(2024, 3, 1));

    private static ValidationFailedException rejected(String amount, String start) {
        return catchThrowableOfType(
                ValidationFailedException.class, () -> SalaryRevision.revise(LATEST, amount, start));
    }

    @Test
    void closesThePreviousRecordTheDayBeforeAndOpensTheNewOneInTheSameCurrency() {
        SalaryRevision revision = SalaryRevision.revise(LATEST, "120000.50", "2025-01-01");

        assertThat(revision.previousEnd()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(revision.newSalary()).isEqualTo(Money.of("120000.50", AUD));
        assertThat(revision.newStart()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    void theDayAfterTheLatestStartIsTheEarliestAllowedDate() {
        SalaryRevision revision = SalaryRevision.revise(LATEST, "1", "2024-03-02");

        assertThat(revision.previousEnd()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    void aStartEqualToTheLatestStartIsRejectedWithTheSpecificCode() {
        ValidationFailedException error = rejected("120000", "2024-03-01");

        assertThat(error.code()).isEqualTo("REVISION_DATE_INVALID");
        assertThat(error.errors()).extracting(FieldError::field).containsExactly("effectiveStart");
    }

    @Test
    void aStartBeforeTheLatestStartIsRejectedWithTheSpecificCode() {
        ValidationFailedException error = rejected("120000", "2023-01-01");

        assertThat(error.code()).isEqualTo("REVISION_DATE_INVALID");
    }

    @Test
    void aFutureDatedStartIsAllowed() {
        SalaryRevision revision = SalaryRevision.revise(LATEST, "130000", "2099-07-01");

        assertThat(revision.newStart()).isEqualTo(LocalDate.of(2099, 7, 1));
        assertThat(revision.previousEnd()).isEqualTo(LocalDate.of(2099, 6, 30));
    }

    @Test
    void anAmountThatIsNotAPositiveMoneyValueIsRejected() {
        for (String bad : new String[] {"0", "-5", "abc", "", "  ", "1e3", "100.123", "1000000000000"}) {
            ValidationFailedException error = rejected(bad, "2025-01-01");
            assertThat(error.code()).as("amount %s", bad).isEqualTo("VALIDATION_FAILED");
            assertThat(error.errors()).as("amount %s", bad).extracting(FieldError::field).containsExactly("amount");
        }
    }

    @Test
    void aMissingOrImpossibleOrAbsurdDateIsRejected() {
        for (String bad : new String[] {null, "", "yesterday", "2025-02-31", "+99999-01-01", "1800-01-01", "2500-01-01"}) {
            ValidationFailedException error = rejected("120000", bad);
            assertThat(error.code()).as("date %s", bad).isEqualTo("VALIDATION_FAILED");
            assertThat(error.errors()).as("date %s", bad).extracting(FieldError::field).containsExactly("effectiveStart");
        }
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        ValidationFailedException error = rejected("-1", "2024-03-01");

        List<String> fields = error.errors().stream().map(FieldError::field).toList();
        assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(fields).containsExactlyInAnyOrder("amount", "effectiveStart");
    }

    @Test
    void anAmountWithSurroundingSpacesIsAccepted() {
        assertThat(SalaryRevision.revise(LATEST, " 95000 ", "2025-01-01").newSalary())
                .isEqualTo(Money.of("95000", AUD));
    }
}
