package com.acme.salary.compensation.domain;

import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A valid salary change: the latest record ends the day before the new one starts, and the new
 * record is open (no end) and in the same currency. Pure rules, no clock and no database; whether
 * the new record is Current today is decided from its dates when the history is read.
 *
 * @param previousEnd the last day of the record being replaced
 * @param newSalary the new amount, in the replaced record's currency
 * @param newStart the first day of the new record
 */
public record SalaryRevision(LocalDate previousEnd, Money newSalary, LocalDate newStart) {

    public static final String DATE_INVALID = "REVISION_DATE_INVALID";

    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$");
    private static final int EARLIEST_YEAR = 1900;
    private static final int LATEST_YEAR = 2100;

    public static SalaryRevision revise(LatestSalary latest, String amountText, String startText) {
        List<FieldError> errors = new ArrayList<>();
        BigDecimal amount = parseAmount(amountText, errors);
        LocalDate start = parseDate(startText, errors);
        boolean notAfterLatest = start != null && !start.isAfter(latest.start());
        if (notAfterLatest) {
            errors.add(new FieldError(
                    "effectiveStart", "must be after the current salary's start date (" + latest.start() + ")"));
        }
        if (!errors.isEmpty()) {
            boolean onlyTheDateOrder = notAfterLatest && errors.size() == 1;
            throw onlyTheDateOrder
                    ? new ValidationFailedException(DATE_INVALID, errors)
                    : new ValidationFailedException(errors);
        }
        return new SalaryRevision(start.minusDays(1), Money.of(amount, latest.amount().currency()), start);
    }

    private static BigDecimal parseAmount(String text, List<FieldError> errors) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            errors.add(new FieldError("amount", "is required"));
            return null;
        }
        if (!AMOUNT.matcher(raw).matches() || new BigDecimal(raw).signum() <= 0) {
            errors.add(new FieldError("amount", "must be a positive amount with at most 2 decimals"));
            return null;
        }
        return new BigDecimal(raw);
    }

    private static LocalDate parseDate(String text, List<FieldError> errors) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            errors.add(new FieldError("effectiveStart", "is required"));
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(raw);
            if (date.getYear() < EARLIEST_YEAR || date.getYear() > LATEST_YEAR) {
                errors.add(new FieldError(
                        "effectiveStart", "must be a date between " + EARLIEST_YEAR + " and " + LATEST_YEAR));
                return null;
            }
            return date;
        } catch (DateTimeParseException notADate) {
            errors.add(new FieldError("effectiveStart", "must be a valid date (YYYY-MM-DD)"));
            return null;
        }
    }
}
