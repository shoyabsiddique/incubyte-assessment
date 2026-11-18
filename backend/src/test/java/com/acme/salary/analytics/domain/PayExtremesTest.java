package com.acme.salary.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayExtremesTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");
    private static final ExchangeRates RATES = ExchangeRates.of(Map.of(INR, new BigDecimal("0.01")));

    private static ActiveSalary p(String employeeId, String amount, Currency currency) {
        return new ActiveSalary(
                employeeId + "-uuid", employeeId, "N " + employeeId, Country.USA, "Eng", "Dev", Money.of(amount, currency));
    }

    private static List<String> ids(List<RankedSalary> ranked) {
        return ranked.stream().map(r -> r.person().employeeId()).toList();
    }

    @Test
    void returnsTheHighestAndLowestByUsdWithMixedCurrencies() {
        List<ActiveSalary> rows = List.of(
                p("EMP-1", "300", USD),
                p("EMP-2", "20000", INR),
                p("EMP-3", "100", USD),
                p("EMP-4", "50", USD),
                p("EMP-5", "250", USD));

        Extremes e = PayExtremes.of(rows, 2, RATES);

        assertThat(ids(e.highest())).containsExactly("EMP-1", "EMP-5");
        assertThat(ids(e.lowest())).containsExactly("EMP-4", "EMP-3");
        assertThat(e.highest().get(1).usd()).isEqualTo(Money.of("250", USD));
    }

    @Test
    void tiesAreBrokenByEmployeeIdInBothLists() {
        List<ActiveSalary> rows = List.of(
                p("EMP-3", "100", USD),
                p("EMP-1", "100", USD),
                p("EMP-2", "100", USD),
                p("EMP-9", "500", USD),
                p("EMP-8", "1", USD));

        Extremes e = PayExtremes.of(rows, 2, RATES);

        assertThat(ids(e.highest())).containsExactly("EMP-9", "EMP-1");
        assertThat(ids(e.lowest())).containsExactly("EMP-8", "EMP-2");
    }

    @Test
    void aDepartmentSmallerThanNLeavesTheLowestListEmptyInsteadOfRepeatingPeople() {
        List<ActiveSalary> rows = List.of(p("EMP-1", "300", USD), p("EMP-2", "200", USD), p("EMP-3", "100", USD));

        Extremes e = PayExtremes.of(rows, 5, RATES);

        assertThat(ids(e.highest())).containsExactly("EMP-1", "EMP-2", "EMP-3");
        assertThat(e.lowest()).isEmpty();
    }

    @Test
    void aDepartmentBetweenNAndTwoNSplitsWithoutOverlap() {
        List<ActiveSalary> rows = List.of(p("EMP-1", "300", USD), p("EMP-2", "200", USD), p("EMP-3", "100", USD));

        Extremes e = PayExtremes.of(rows, 2, RATES);

        assertThat(ids(e.highest())).containsExactly("EMP-1", "EMP-2");
        assertThat(ids(e.lowest())).containsExactly("EMP-3");
    }

    @Test
    void anEmptyDepartmentHasNoExtremes() {
        Extremes e = PayExtremes.of(List.of(), 3, RATES);

        assertThat(e.highest()).isEmpty();
        assertThat(e.lowest()).isEmpty();
    }
}
