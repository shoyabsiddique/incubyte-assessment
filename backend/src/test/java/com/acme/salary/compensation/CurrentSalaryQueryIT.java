package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.compensation.api.CurrentSalaryQuery;
import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The salary lookups other modules rely on, against real rows (dates are relative to today). */
class CurrentSalaryQueryIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private CurrentSalaryQuery salaries;

    @Autowired
    private com.acme.salary.compensation.api.ExchangeRateQuery rates;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM compensation_record");
        jdbc.update("DELETE FROM employee");
    }

    private UUID employee(int n) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO employee (id, employee_id, first_name, last_name, work_email, job_title, department, "
                        + "country, city, employment_type, hire_date, status) VALUES (?, ?, 'A', 'B', ?, 'T', 'D', 'USA', 'C', 'FULL_TIME', '2015-01-01', 'ACTIVE')",
                id.toString(), "EMP-8" + String.format("%05d", n), "e" + n + "@acme.example");
        return id;
    }

    private void record(UUID employee, String amount, String currency, LocalDate start, LocalDate end) {
        jdbc.update("INSERT INTO compensation_record (id, employee_id, amount, currency, effective_start, effective_end) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), employee.toString(), amount, currency, start, end);
    }

    @Test
    void returnsTheRecordWhoseRangeCoversToday() {
        UUID id = employee(1);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        record(id, "100.00", "USD", today.minusYears(3), today.minusYears(1).minusDays(1));
        record(id, "120.00", "USD", today.minusYears(1), null);

        Map<UUID, Money> result = salaries.currentSalaries(List.of(id));

        assertThat(result.get(id)).isEqualTo(Money.of("120.00", Currency.getInstance("USD")));
    }

    @Test
    void whenTwoRecordsCoverTodayTheLatestStartWinsEveryTime() {
        UUID id = employee(5);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        record(id, "100.00", "USD", today.minusYears(2), today.plusDays(5));
        record(id, "130.00", "USD", today.minusYears(1), null);
        record(id, "115.00", "USD", today.minusMonths(6), today.plusDays(1));

        for (int i = 0; i < 5; i++) {
            assertThat(salaries.currentSalaries(List.of(id)).get(id))
                    .isEqualTo(Money.of("115.00", Currency.getInstance("USD")));
        }
    }

    @Test
    void ignoresARecordThatHasNotStartedYetAndOneThatAlreadyEnded() {
        UUID id = employee(2);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        record(id, "90.00", "USD", today.minusYears(2), today.minusDays(1));
        record(id, "150.00", "USD", today.plusDays(30), null);

        assertThat(salaries.currentSalaries(List.of(id))).doesNotContainKey(id);
    }

    @Test
    void treatsARecordEndingTodayAndOneStartingTodayAsCurrent() {
        UUID endsToday = employee(3);
        UUID startsToday = employee(4);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        record(endsToday, "80.00", "USD", today.minusYears(1), today);
        record(startsToday, "95.00", "USD", today, null);

        Map<UUID, Money> result = salaries.currentSalaries(List.of(endsToday, startsToday));

        assertThat(result).containsOnlyKeys(endsToday, startsToday);
    }

    @Test
    void handlesMoreIdsThanOneQueryCanTake() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 1_250; i++) {
            UUID id = employee(100 + i);
            record(id, "10.00", "USD", today.minusYears(1), null);
            ids.add(id);
        }

        assertThat(salaries.currentSalaries(ids)).hasSize(1_250);
    }

    @Test
    void whenManyPeopleAreWantedOnlyThoseAskedForComeBack() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        List<UUID> wanted = new ArrayList<>();
        for (int i = 0; i < 1_100; i++) {
            UUID id = employee(2_000 + i);
            record(id, "10.00", "USD", today.minusYears(1), null);
            wanted.add(id);
        }
        List<UUID> notWanted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID id = employee(4_000 + i);
            record(id, "10.00", "USD", today.minusYears(1), null);
            notWanted.add(id);
        }

        Map<UUID, Money> result = salaries.currentSalaries(wanted);

        assertThat(result).hasSize(1_100).doesNotContainKeys(notWanted.toArray(UUID[]::new));
    }

    @Test
    void anEmptyListOfIdsGivesAnEmptyResult() {
        assertThat(salaries.currentSalaries(List.of())).isEmpty();
        assertThat(salaries.rankByCurrentUsd(List.of(), true, rates.snapshot())).isEmpty();
    }

    @Test
    void ranksAcrossCurrenciesByUsdEquivalentWithPeopleWithoutSalaryLast() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        UUID rupees = employee(10); // 5,000,000 INR at 0.012 = 60,000 USD
        UUID dollars = employee(11); // 70,000 USD
        UUID pounds = employee(12); // 50,000 GBP at 1.27 = 63,500 USD
        UUID nobody = employee(13);
        record(rupees, "5000000.00", "INR", today.minusYears(1), null);
        record(dollars, "70000.00", "USD", today.minusYears(1), null);
        record(pounds, "50000.00", "GBP", today.minusYears(1), null);

        List<UUID> all = List.of(rupees, dollars, pounds, nobody);

        assertThat(salaries.rankByCurrentUsd(all, true, rates.snapshot())).containsExactly(dollars, pounds, rupees, nobody);
        assertThat(salaries.rankByCurrentUsd(all, false, rates.snapshot())).containsExactly(rupees, pounds, dollars, nobody);
    }

    @Test
    void equalSalariesKeepTheOrderTheCallerGave() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        UUID first = employee(20);
        UUID second = employee(21);
        UUID third = employee(22);
        for (UUID id : List.of(first, second, third)) {
            record(id, "50000.00", "USD", today.minusYears(1), null);
        }

        assertThat(salaries.rankByCurrentUsd(List.of(second, third, first), true, rates.snapshot())).containsExactly(second, third, first);
        assertThat(salaries.rankByCurrentUsd(List.of(second, third, first), false, rates.snapshot())).containsExactly(second, third, first);
    }
}
