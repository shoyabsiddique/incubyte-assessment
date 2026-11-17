package com.acme.salary.compensation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.compensation.persistence.CompensationRecordEntity;
import com.acme.salary.compensation.persistence.CompensationRecordRepository;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ExchangeRates;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalaryHistoryServiceTest {

    private static final UUID EMPLOYEE = UUID.randomUUID();
    private final CompensationRecordRepository records = mock(CompensationRecordRepository.class);
    private final ExchangeRateQuery rates = mock(ExchangeRateQuery.class);

    private SalaryHistoryService serviceOn(String day) {
        Clock clock = Clock.fixed(Instant.parse(day + "T12:00:00Z"), ZoneOffset.UTC);
        when(rates.snapshot()).thenReturn(ExchangeRates.of(Map.of(java.util.Currency.getInstance("AUD"), new BigDecimal("0.65"))));
        return new SalaryHistoryService(records, rates, clock);
    }

    private static CompensationRecordEntity record(String start, String end) {
        CompensationRecordEntity entity = mock(CompensationRecordEntity.class);
        when(entity.getId()).thenReturn(UUID.randomUUID());
        when(entity.getAmount()).thenReturn(new BigDecimal("100000.0000"));
        when(entity.getCurrency()).thenReturn("AUD");
        when(entity.getEffectiveStart()).thenReturn(LocalDate.parse(start));
        when(entity.getEffectiveEnd()).thenReturn(end == null ? null : LocalDate.parse(end));
        return entity;
    }

    @Test
    void marksTheRecordCoveringTheClockDateAsCurrent() {
        CompensationRecordEntity r0 = record("2023-06-01", null);
        CompensationRecordEntity r1 = record("2021-02-10", "2023-05-31");
        when(records.findByEmployeeIdOrderByEffectiveStartDesc(EMPLOYEE)).thenReturn(List.of(r0, r1));

        assertThat(serviceOn("2022-01-01").historyOf(EMPLOYEE)).extracting(SalaryHistoryEntry::current).containsExactly(false, true);
    }

    @Test
    void marksNoRecordCurrentWhenNothingCoversToday() {
        CompensationRecordEntity r0 = record("2030-01-01", null);
        CompensationRecordEntity r1 = record("2020-01-01", "2024-12-31");
        when(records.findByEmployeeIdOrderByEffectiveStartDesc(EMPLOYEE)).thenReturn(List.of(r0, r1));

        assertThat(serviceOn("2026-11-18").historyOf(EMPLOYEE)).extracting(SalaryHistoryEntry::current).containsExactly(false, false);
    }

    @Test
    void marksOnlyTheNewestRecordWhenPeriodsOverlap() {
        CompensationRecordEntity r0 = record("2024-01-01", null);
        CompensationRecordEntity r1 = record("2020-01-01", null);
        when(records.findByEmployeeIdOrderByEffectiveStartDesc(EMPLOYEE)).thenReturn(List.of(r0, r1));

        assertThat(serviceOn("2026-11-18").historyOf(EMPLOYEE)).extracting(SalaryHistoryEntry::current).containsExactly(true, false);
    }

    @Test
    void convertsEveryRecordWithOneRateSnapshot() {
        CompensationRecordEntity r0 = record("2024-01-01", null);
        CompensationRecordEntity r1 = record("2020-01-01", "2023-12-31");
        when(records.findByEmployeeIdOrderByEffectiveStartDesc(EMPLOYEE)).thenReturn(List.of(r0, r1));
        SalaryHistoryService service = serviceOn("2026-11-18");

        List<SalaryHistoryEntry> history = service.historyOf(EMPLOYEE);

        verify(rates, times(1)).snapshot();
        assertThat(history).allSatisfy(entry -> assertThat(entry.amountUsd().amount()).isEqualByComparingTo("65000"));
    }

    @Test
    void anEmployeeWithoutRecordsHasNoHistory() {
        when(records.findByEmployeeIdOrderByEffectiveStartDesc(EMPLOYEE)).thenReturn(List.of());

        assertThatThrownBy(() -> serviceOn("2026-11-18").historyOf(EMPLOYEE))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("SALARY_HISTORY_NOT_FOUND"));
    }
}
