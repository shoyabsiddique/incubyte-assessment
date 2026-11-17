package com.acme.salary.employees.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import org.junit.jupiter.api.Test;

class DirectoryQueryTest {

    private static DirectoryQuery query(String page, String size, String sort, String q, String country,
            String department, String jobTitle, String type, String status) {
        return DirectoryQuery.of(page, size, sort, q, country, department, jobTitle, type, status);
    }

    private static DirectoryQuery none() {
        return query(null, null, null, null, null, null, null, null, null);
    }

    private static void assertRejected(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_INPUT);
                });
    }

    @Test
    void defaultsToTheFirstPageOfTwentyOrderedByEmployeeId() {
        DirectoryQuery q = none();

        assertThat(q.page()).isZero();
        assertThat(q.size()).isEqualTo(20);
        assertThat(q.sortField()).isEqualTo(SortField.EMPLOYEE_ID);
        assertThat(q.direction()).isEqualTo(SortDirection.ASC);
        assertThat(q.searchTokens()).isEmpty();
        assertThat(q.country()).isEmpty();
        assertThat(q.status()).isEmpty();
    }

    @Test
    void acceptsAPageSizeUpToTheHardMaximumOfOneHundred() {
        assertThat(query("3", "100", null, null, null, null, null, null, null).size()).isEqualTo(100);
        assertThat(query("3", "100", null, null, null, null, null, null, null).page()).isEqualTo(3);
        assertThat(query(null, "1", null, null, null, null, null, null, null).size()).isEqualTo(1);
    }

    @Test
    void rejectsAPageSizeAboveTheMaximum() {
        assertRejected(() -> query(null, "101", null, null, null, null, null, null, null), "PAGE_SIZE_TOO_LARGE");
    }

    @Test
    void rejectsPagesAndSizesThatAreNotPositiveWholeNumbers() {
        assertRejected(() -> query("-1", null, null, null, null, null, null, null, null), "INVALID_PAGE");
        assertRejected(() -> query("abc", null, null, null, null, null, null, null, null), "INVALID_PAGE");
        assertRejected(() -> query(null, "0", null, null, null, null, null, null, null), "INVALID_PAGE");
        assertRejected(() -> query(null, "twenty", null, null, null, null, null, null, null), "INVALID_PAGE");
    }

    @Test
    void parsesEveryWhitelistedSortFieldWithAnOptionalDirection() {
        assertThat(query(null, null, "salary,desc", null, null, null, null, null, null).sortField()).isEqualTo(SortField.SALARY);
        assertThat(query(null, null, "salary,desc", null, null, null, null, null, null).direction()).isEqualTo(SortDirection.DESC);
        assertThat(query(null, null, "name", null, null, null, null, null, null).sortField()).isEqualTo(SortField.NAME);
        assertThat(query(null, null, "name", null, null, null, null, null, null).direction()).isEqualTo(SortDirection.ASC);
        for (String field : new String[] {"employeeId", "name", "jobTitle", "department", "country", "hireDate", "status", "salary"}) {
            assertThat(query(null, null, field + ",asc", null, null, null, null, null, null).sortField()).isNotNull();
        }
    }

    @Test
    void rejectsSortFieldsAndDirectionsOutsideTheWhitelist() {
        assertRejected(() -> query(null, null, "password,asc", null, null, null, null, null, null), "INVALID_SORT");
        assertRejected(() -> query(null, null, "name,sideways", null, null, null, null, null, null), "INVALID_SORT");
        assertRejected(() -> query(null, null, "name,asc,extra", null, null, null, null, null, null), "INVALID_SORT");
    }

    @Test
    void parsesFiltersIgnoringCaseAndSurroundingSpaces() {
        DirectoryQuery q = query(null, null, null, null, " india ", "Engineering ", " Software Engineer", "contract", "On_Leave");

        assertThat(q.country()).contains(Country.INDIA);
        assertThat(q.department()).contains("Engineering");
        assertThat(q.jobTitle()).contains("Software Engineer");
        assertThat(q.employmentType()).contains(EmploymentType.CONTRACT);
        assertThat(q.status()).contains(EmploymentStatus.ON_LEAVE);
    }

    @Test
    void treatsBlankFiltersAsNotSet() {
        DirectoryQuery q = query(null, null, null, "  ", " ", "", "  ", "", " ");

        assertThat(q.country()).isEmpty();
        assertThat(q.department()).isEmpty();
        assertThat(q.jobTitle()).isEmpty();
        assertThat(q.employmentType()).isEmpty();
        assertThat(q.status()).isEmpty();
        assertThat(q.searchTokens()).isEmpty();
    }

    @Test
    void rejectsFilterValuesThatDoNotExist() {
        assertRejected(() -> query(null, null, null, null, "Atlantis", null, null, null, null), "INVALID_FILTER");
        assertRejected(() -> query(null, null, null, null, null, null, null, "INTERN", null), "INVALID_FILTER");
        assertRejected(() -> query(null, null, null, null, null, null, null, null, "RETIRED"), "INVALID_FILTER");
    }

    @Test
    void splitsTheSearchIntoWordsOnAnyWhitespace() {
        assertThat(query(null, null, null, "  priya \t nair\n", null, null, null, null, null).searchTokens())
                .containsExactly("priya", "nair");
    }

    @Test
    void refusesSearchesWithAbsurdlyManyOrLongWords() {
        assertRejected(() -> query(null, null, null, "a b c d e f g h i j k", null, null, null, null, null), "INVALID_SEARCH");
        assertRejected(() -> query(null, null, null, "x".repeat(101), null, null, null, null, null), "INVALID_SEARCH");
    }
}
