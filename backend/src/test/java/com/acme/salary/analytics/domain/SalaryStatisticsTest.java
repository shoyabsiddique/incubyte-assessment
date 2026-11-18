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

class SalaryStatisticsTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency AUD = Currency.getInstance("AUD");
    private static final ExchangeRates RATES =
            ExchangeRates.of(Map.of(INR, new BigDecimal("0.01"), AUD, new BigDecimal("0.5")));

    private static int next;

    private static ActiveSalary person(Country country, String department, String title, String amount, Currency currency) {
        String id = "EMP-" + (++next);
        return new ActiveSalary(id + "-uuid", id, "Person " + id, country, department, title, Money.of(amount, currency));
    }

    private static ActiveSalary usd(String amount) {
        return person(Country.USA, "Eng", "Dev", amount, USD);
    }

    private static GroupStats only(List<ActiveSalary> rows) {
        List<GroupStats> groups = SalaryStatistics.calculate(rows, GroupBy.COUNTRY, RATES, false);
        assertThat(groups).hasSize(1);
        return groups.get(0);
    }

    @Test
    void anOddSizedGroupHasTheMiddleValueAsMedian() {
        Stats s = only(List.of(usd("10"), usd("30"), usd("20"))).usd();

        assertThat(s.count()).isEqualTo(3);
        assertThat(s.median()).isEqualTo(Money.of("20", USD));
        assertThat(s.average()).isEqualTo(Money.of("20", USD));
        assertThat(s.minimum()).isEqualTo(Money.of("10", USD));
        assertThat(s.maximum()).isEqualTo(Money.of("30", USD));
        assertThat(s.total()).isEqualTo(Money.of("60", USD));
    }

    @Test
    void anEvenSizedGroupHasTheExactMeanOfTheTwoMiddleValuesAsMedian() {
        Stats s = only(List.of(usd("10"), usd("40"), usd("20"), usd("31"))).usd();

        assertThat(s.median()).isEqualTo(Money.of("25.5", USD));
    }

    @Test
    void aSingleRowGroupHasEverythingEqual() {
        Stats s = only(List.of(usd("77.25"))).usd();

        assertThat(s.count()).isEqualTo(1);
        assertThat(List.of(s.average(), s.median(), s.minimum(), s.maximum(), s.total()))
                .containsOnly(Money.of("77.25", USD));
    }

    @Test
    void noRowsMeansNoGroups() {
        assertThat(SalaryStatistics.calculate(List.of(), GroupBy.COUNTRY, RATES, false)).isEmpty();
    }

    @Test
    void mixedCurrenciesAreComparedInUsdAndTheMedianIsOverAllRowsNotAnAverageOfMedians() {
        List<ActiveSalary> rows = List.of(
                person(Country.USA, "Eng", "Dev", "100", USD),
                person(Country.INDIA, "Eng", "Dev", "20000", INR), // 200 USD
                person(Country.AUSTRALIA, "Eng", "Dev", "1000", AUD)); // 500 USD

        Stats s = SalaryStatistics.calculate(rows, GroupBy.DEPARTMENT, RATES, false).get(0).usd();

        assertThat(s.median()).isEqualTo(Money.of("200", USD));
        assertThat(s.minimum()).isEqualTo(Money.of("100", USD));
        assertThat(s.maximum()).isEqualTo(Money.of("500", USD));
        assertThat(s.total()).isEqualTo(Money.of("800", USD));
    }

    @Test
    void groupsAreSortedByKeyAndSplitByTheChosenField() {
        List<ActiveSalary> rows = List.of(
                person(Country.UK, "Sales", "Rep", "5", USD),
                person(Country.USA, "Eng", "Dev", "5", USD),
                person(Country.USA, "Eng", "Lead", "5", USD));

        assertThat(SalaryStatistics.calculate(rows, GroupBy.DEPARTMENT, RATES, false))
                .extracting(GroupStats::key)
                .containsExactly("Eng", "Sales");
        assertThat(SalaryStatistics.calculate(rows, GroupBy.JOB_TITLE, RATES, false))
                .extracting(GroupStats::key)
                .containsExactly("Dev", "Lead", "Rep");
        assertThat(SalaryStatistics.calculate(rows, GroupBy.COUNTRY, RATES, false))
                .extracting(GroupStats::key)
                .containsExactly("UK", "USA");
    }

    @Test
    void localStatisticsAreInTheLocalCurrencyWhenAskedFor() {
        List<ActiveSalary> rows = List.of(
                person(Country.INDIA, "Eng", "Dev", "1000", INR), person(Country.INDIA, "Eng", "Dev", "3000", INR));

        GroupStats group = SalaryStatistics.calculate(rows, GroupBy.COUNTRY, RATES, true).get(0);

        assertThat(group.local().average()).isEqualTo(Money.of("2000", INR));
        assertThat(group.usd().average()).isEqualTo(Money.of("20", USD));
        assertThat(SalaryStatistics.calculate(rows, GroupBy.COUNTRY, RATES, false).get(0).local()).isNull();
    }

    @Test
    void groupByParsesTheApiNames() {
        assertThat(GroupBy.parse("country")).isEqualTo(GroupBy.COUNTRY);
        assertThat(GroupBy.parse("department")).isEqualTo(GroupBy.DEPARTMENT);
        assertThat(GroupBy.parse("jobTitle")).isEqualTo(GroupBy.JOB_TITLE);
    }
}
