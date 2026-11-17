package com.acme.salary.employees.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeDraftTest {

    private static ProfileInput valid() {
        return new ProfileInput(
                "Ada", "Lovelace", "ada@acme.example", "Software Engineer", "Engineering", "AUSTRALIA", "Sydney",
                "FULL_TIME", "2024-03-01", "ACTIVE");
    }

    private static List<String> failedFields(Runnable action) {
        return org.assertj.core.api.Assertions.catchThrowableOfType(ValidationFailedException.class, action::run)
                .errors()
                .stream()
                .map(FieldError::field)
                .toList();
    }

    @Test
    void parsesAValidProfile() {
        EmployeeDraft draft = EmployeeDraft.parse(valid());

        assertThat(draft.firstName()).isEqualTo("Ada");
        assertThat(draft.country()).isEqualTo(Country.AUSTRALIA);
        assertThat(draft.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(draft.status()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(draft.hireDate()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    void trimsAndCollapsesWhitespaceInTextFields() {
        ProfileInput messy = new ProfileInput(
                "  Ada ", " Lovelace", " ada@acme.example ", "  Senior   Software\tEngineer ", "  Platform   Engineering ",
                "AUSTRALIA", "  Sydney ", "FULL_TIME", "2024-03-01", "ACTIVE");

        EmployeeDraft draft = EmployeeDraft.parse(messy);

        assertThat(draft.jobTitle()).isEqualTo("Senior Software Engineer");
        assertThat(draft.department()).isEqualTo("Platform Engineering");
        assertThat(draft.firstName()).isEqualTo("Ada");
        assertThat(draft.workEmail()).isEqualTo("ada@acme.example");
        assertThat(draft.city()).isEqualTo("Sydney");
    }

    @Test
    void reportsEveryInvalidFieldAtOnce() {
        ProfileInput broken = new ProfileInput("", null, "not-an-email", " ", null, "ATLANTIS", "", "SOMETIMES", "yesterday", "GONE");

        List<String> fields = failedFields(() -> EmployeeDraft.parse(broken));

        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "firstName", "lastName", "workEmail", "jobTitle", "department", "country", "city",
                        "employmentType", "hireDate", "status");
    }

    @Test
    void rejectsTextLongerThanTheColumnAllows() {
        ProfileInput tooLong = new ProfileInput(
                "A".repeat(101), "L", "a@b.co", "T", "D", "INDIA", "C", "FULL_TIME", "2024-03-01", "ACTIVE");

        assertThat(failedFields(() -> EmployeeDraft.parse(tooLong))).containsExactly("firstName");
    }

    @Test
    void rejectsAnImpossibleHireDate() {
        ProfileInput impossible = new ProfileInput(
                "A", "L", "a@b.co", "T", "D", "INDIA", "C", "FULL_TIME", "2024-02-31", "ACTIVE");

        assertThat(failedFields(() -> EmployeeDraft.parse(impossible))).containsExactly("hireDate");
    }

    @Test
    void aNewEmployeeCarriesTheStartingSalaryInTheCountryCurrency() {
        NewEmployee created = NewEmployee.parse(valid(), new SalaryInput("145700.50", "AUD", "2024-03-01"));

        assertThat(created.salaryAmount()).isEqualByComparingTo(new BigDecimal("145700.50"));
        assertThat(created.salaryCurrency()).isEqualTo(Currency.getInstance("AUD"));
        assertThat(created.salaryEffectiveStart()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    void aSalaryInTheWrongCurrencyForTheCountryIsRejected() {
        assertThat(failedFields(() -> NewEmployee.parse(valid(), new SalaryInput("100", "USD", "2024-03-01"))))
                .containsExactly("salaryCurrency");
    }

    @Test
    void salaryMustBeAPositiveAmountWithAStartDate() {
        for (String bad : new String[] {"0", "-5", "abc", "", "1e3", "100.123", "1000000000000"}) {
            assertThat(failedFields(() -> NewEmployee.parse(valid(), new SalaryInput(bad, "AUD", "2024-03-01"))))
                    .as("amount %s", bad)
                    .containsExactly("salaryAmount");
        }
        assertThat(failedFields(() -> NewEmployee.parse(valid(), new SalaryInput("100", "AUD", null))))
                .containsExactly("salaryEffectiveStart");
    }

    @Test
    void profileErrorsAndSalaryErrorsAreReportedTogether() {
        ProfileInput bad = new ProfileInput("", "L", "a@b.co", "T", "D", "INDIA", "C", "FULL_TIME", "2024-03-01", "ACTIVE");

        assertThatThrownBy(() -> NewEmployee.parse(bad, new SalaryInput("-1", "INR", "2024-03-01")))
                .isInstanceOfSatisfying(ValidationFailedException.class, e -> assertThat(e.errors())
                        .extracting(FieldError::field)
                        .containsExactlyInAnyOrder("firstName", "salaryAmount"));
    }
}
