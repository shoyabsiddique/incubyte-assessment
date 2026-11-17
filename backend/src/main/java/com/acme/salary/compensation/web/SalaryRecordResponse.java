package com.acme.salary.compensation.web;

import com.acme.salary.compensation.application.SalaryHistoryEntry;
import com.acme.salary.shared.web.MoneyResponse;

record SalaryRecordResponse(
        String id,
        MoneyResponse amount,
        MoneyResponse amountUsd,
        String effectiveStart,
        String effectiveEnd,
        boolean current) {

    static SalaryRecordResponse from(SalaryHistoryEntry entry) {
        return new SalaryRecordResponse(
                entry.id().toString(),
                MoneyResponse.from(entry.amount()),
                MoneyResponse.from(entry.amountUsd()),
                entry.effectiveStart().toString(),
                entry.effectiveEnd() == null ? null : entry.effectiveEnd().toString(),
                entry.current());
    }
}
