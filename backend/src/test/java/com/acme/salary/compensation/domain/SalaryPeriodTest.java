package com.acme.salary.compensation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SalaryPeriodTest {

    private static final LocalDate START = LocalDate.of(2024, 3, 1);
    private static final LocalDate END = LocalDate.of(2025, 2, 28);

    @Test
    void aClosedPeriodCoversEveryDayFromStartToEndInclusive() {
        SalaryPeriod period = new SalaryPeriod(START, END);

        assertThat(period.covers(START)).isTrue();
        assertThat(period.covers(LocalDate.of(2024, 9, 15))).isTrue();
        assertThat(period.covers(END)).isTrue();
    }

    @Test
    void aClosedPeriodDoesNotCoverTheDaysAroundIt() {
        SalaryPeriod period = new SalaryPeriod(START, END);

        assertThat(period.covers(START.minusDays(1))).isFalse();
        assertThat(period.covers(END.plusDays(1))).isFalse();
    }

    @Test
    void anOpenPeriodCoversEverythingFromItsStartOnwards() {
        SalaryPeriod period = new SalaryPeriod(START, null);

        assertThat(period.isOpen()).isTrue();
        assertThat(period.covers(START)).isTrue();
        assertThat(period.covers(LocalDate.of(2099, 1, 1))).isTrue();
        assertThat(period.covers(START.minusDays(1))).isFalse();
    }

    @Test
    void aClosedPeriodIsNotOpen() {
        assertThat(new SalaryPeriod(START, END).isOpen()).isFalse();
    }
}
