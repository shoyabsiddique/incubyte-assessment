package com.acme.salary.simulator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.analytics.api.UsdPay;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.Rounding;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncrementSimulatorTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static UsdPay pay(Country country, String department, String usd) {
        return new UsdPay(country, department, Money.of(usd, USD));
    }

    private static final List<UsdPay> PEOPLE = List.of(
            pay(Country.USA, "Engineering", "100000"),
            pay(Country.USA, "Sales", "50000"),
            pay(Country.INDIA, "Engineering", "30000"),
            pay(Country.INDIA, "Sales", "20000"));

    @Test
    void zeroPercentCostsNothingAndKeepsPayroll() {
        SimulationResult r = IncrementSimulator.simulate(PEOPLE, new BigDecimal("0"), SimulationScope.organization());

        assertThat(r.current()).isEqualTo(Money.of("200000", USD));
        assertThat(r.additional()).isEqualTo(Money.of("0", USD));
        assertThat(r.projected()).isEqualTo(Money.of("200000", USD));
        assertThat(r.employees()).isEqualTo(4);
    }

    @Test
    void tenPercentForTheWholeOrganisation() {
        SimulationResult r = IncrementSimulator.simulate(PEOPLE, new BigDecimal("10"), SimulationScope.organization());

        assertThat(r.current()).isEqualTo(Money.of("200000", USD));
        assertThat(r.projected()).isEqualTo(Money.of("220000", USD));
        assertThat(r.additional()).isEqualTo(Money.of("20000", USD));
        assertThat(r.employees()).isEqualTo(4);
    }

    @Test
    void aCountryScopeOnlyCountsThatCountry() {
        SimulationResult r = IncrementSimulator.simulate(PEOPLE, new BigDecimal("10"), SimulationScope.country(Country.INDIA));

        assertThat(r.current()).isEqualTo(Money.of("50000", USD));
        assertThat(r.additional()).isEqualTo(Money.of("5000", USD));
        assertThat(r.employees()).isEqualTo(2);
    }

    @Test
    void aDepartmentScopeCountsThatDepartmentEverywhere() {
        SimulationResult r =
                IncrementSimulator.simulate(PEOPLE, new BigDecimal("50"), SimulationScope.department("Engineering"));

        assertThat(r.current()).isEqualTo(Money.of("130000", USD));
        assertThat(r.projected()).isEqualTo(Money.of("195000", USD));
        assertThat(r.employees()).isEqualTo(2);
    }

    @Test
    void aScopeWithNobodyInItIsAllZeros() {
        SimulationResult r =
                IncrementSimulator.simulate(PEOPLE, new BigDecimal("10"), SimulationScope.country(Country.CANADA));

        assertThat(r.employees()).isZero();
        assertThat(List.of(r.current(), r.additional(), r.projected())).containsOnly(Money.of("0", USD));
    }

    @Test
    void arithmeticIsNotRoundedAndOnlyThePresentedValuesAre() {
        List<UsdPay> people = List.of(pay(Country.USA, "Eng", "33.335"), pay(Country.USA, "Eng", "0.001"));

        SimulationResult r = IncrementSimulator.simulate(people, new BigDecimal("12.5"), SimulationScope.organization());

        assertThat(r.current().amount()).isEqualByComparingTo("33.336");
        assertThat(r.projected().amount()).isEqualByComparingTo("37.503");
        assertThat(r.additional().amount()).isEqualByComparingTo("4.167");
        assertThat(Rounding.display(r.projected().amount())).isEqualByComparingTo("37.50");
        assertThat(Rounding.display(r.additional().amount())).isEqualByComparingTo("4.17");
    }

    @Test
    void projectedIsExactlyCurrentTimesOnePlusPercent() {
        SimulationResult r = IncrementSimulator.simulate(PEOPLE, new BigDecimal("7.35"), SimulationScope.organization());

        assertThat(r.projected().amount())
                .isEqualByComparingTo(r.current().amount().multiply(new BigDecimal("1.0735")));
        assertThat(r.additional().amount()).isEqualByComparingTo(r.projected().amount().subtract(r.current().amount()));
    }
}
