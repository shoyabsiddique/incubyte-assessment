package com.acme.salary.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency AUD = Currency.getInstance("AUD");
    private static final ExchangeRates RATES =
            ExchangeRates.of(Map.of(INR, new BigDecimal("0.012"), AUD, new BigDecimal("0.65")));

    private static int next;

    private static ActiveSalary usdPerson(String department, String title, String amount) {
        String id = "EMP-" + String.format("%06d", ++next);
        return new ActiveSalary(id, id, "N " + id, Country.USA, department, title, Money.of(amount, USD));
    }

    /** Four people at 100 plus one at {@code fifth}: the median stays 100. */
    private static List<ActiveSalary> groupOfFive(String fifth) {
        List<ActiveSalary> group = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            group.add(usdPerson("Eng", "Dev", "100"));
        }
        group.add(usdPerson("Eng", "Dev", fifth));
        return group;
    }

    @Test
    void exactlyTwentyPercentAboveOrBelowIsNotFlagged() {
        assertThat(AnomalyDetector.detect(groupOfFive("120"), RATES)).isEmpty();
        assertThat(AnomalyDetector.detect(groupOfFive("80"), RATES)).isEmpty();
    }

    @Test
    void justOverTwentyPercentAboveOrBelowIsFlaggedWithItsSignedDeviation() {
        List<Anomaly> above = AnomalyDetector.detect(groupOfFive("120.01"), RATES);
        List<Anomaly> below = AnomalyDetector.detect(groupOfFive("79.99"), RATES);

        assertThat(above).hasSize(1);
        assertThat(above.get(0).deviationPercent()).isEqualByComparingTo("20.01");
        assertThat(above.get(0).direction()).isEqualTo(Direction.ABOVE);
        assertThat(above.get(0).median()).isEqualTo(Money.of("100", USD));
        assertThat(above.get(0).groupSize()).isEqualTo(5);
        assertThat(below).hasSize(1);
        assertThat(below.get(0).deviationPercent()).isEqualByComparingTo("-20.01");
        assertThat(below.get(0).direction()).isEqualTo(Direction.BELOW);
    }

    @Test
    void aGroupOfFourIsNeverFlaggedButAGroupOfFiveMayBe() {
        List<ActiveSalary> four = new ArrayList<>(groupOfFive("500"));
        four.remove(0);

        assertThat(AnomalyDetector.detect(four, RATES)).isEmpty();
        assertThat(AnomalyDetector.detect(groupOfFive("500"), RATES)).hasSize(1);
    }

    @Test
    void anEvenSizedGroupUsesTheMeanOfTheTwoMiddleValuesAsMedian() {
        List<ActiveSalary> group = new ArrayList<>();
        for (String amount : new String[] {"100", "100", "100", "110", "110", "126"}) {
            group.add(usdPerson("Eng", "Dev", amount));
        }
        // median = (100 + 110) / 2 = 105 and 105 * 1.2 = 126, so 126 is exactly 20% above
        assertThat(AnomalyDetector.detect(group, RATES)).isEmpty();

        group.set(5, usdPerson("Eng", "Dev", "126.01"));
        assertThat(AnomalyDetector.detect(group, RATES)).hasSize(1);
    }

    @Test
    void peerGroupsAreCountryDepartmentAndTitleComparedWithoutCaseOrOuterSpaces() {
        List<ActiveSalary> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            rows.add(usdPerson(i % 2 == 0 ? "Eng" : " eng ", "Dev", "100"));
        }
        rows.add(usdPerson("ENG", "dev ", "300"));
        // the same words in another department are a different group and never join this one
        for (int i = 0; i < 5; i++) {
            rows.add(usdPerson("Sales", "Dev", "100"));
        }

        List<Anomaly> flagged = AnomalyDetector.detect(rows, RATES);

        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).groupSize()).isEqualTo(5);
    }

    @Test
    void aPersonInAnotherCountryIsNotComparedWithThisGroup() {
        List<ActiveSalary> rows = new ArrayList<>(groupOfFive("100"));
        rows.add(new ActiveSalary("x", "EMP-999999", "Far", Country.UK, "Eng", "Dev", Money.of("900", USD)));

        assertThat(AnomalyDetector.detect(rows, RATES)).isEmpty();
    }

    @Test
    void salariesInDifferentCurrenciesAreComparedInUsdAfterACountryEdit() {
        List<ActiveSalary> rows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String id = "EMP-00010" + i;
            rows.add(new ActiveSalary(id, id, "N", Country.INDIA, "Eng", "Dev", Money.of("8000000", INR))); // 96,000 USD
        }
        // moved to India but still paid in AUD: 200,000 AUD = 130,000 USD, 35.4% above the median
        rows.add(new ActiveSalary("m", "EMP-000109", "Moved", Country.INDIA, "Eng", "Dev", Money.of("200000", AUD)));

        List<Anomaly> flagged = AnomalyDetector.detect(rows, RATES);

        assertThat(flagged).hasSize(1);
        assertThat(flagged.get(0).person().employeeId()).isEqualTo("EMP-000109");
        assertThat(flagged.get(0).usd()).isEqualTo(Money.of("130000", USD));
        assertThat(flagged.get(0).median()).isEqualTo(Money.of("96000", USD));
        assertThat(flagged.get(0).deviationPercent().setScale(1, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("35.4");
    }
}
