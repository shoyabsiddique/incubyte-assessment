package com.acme.salary.employees.domain;

import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

/** A valid person to add: their profile plus the salary they start on, paid in their country's currency. */
public record NewEmployee(
        EmployeeDraft profile, BigDecimal salaryAmount, Currency salaryCurrency, LocalDate salaryEffectiveStart) {

    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,12}(\\.\\d{1,2})?$");

    public static NewEmployee parse(ProfileInput profile, SalaryInput salary) {
        List<FieldError> errors = new ArrayList<>();
        EmployeeDraft draft = EmployeeDraft.parse(profile, errors);

        BigDecimal amount = null;
        String raw = salary.amount() == null ? "" : salary.amount().trim();
        if (raw.isEmpty()) {
            errors.add(new FieldError("salaryAmount", "is required"));
        } else if (!AMOUNT.matcher(raw).matches() || new BigDecimal(raw).signum() <= 0) {
            errors.add(new FieldError("salaryAmount", "must be a positive amount with at most 2 decimals"));
        } else {
            amount = new BigDecimal(raw);
        }

        Currency currency = null;
        String code = salary.currency() == null ? "" : salary.currency().trim();
        if (code.isEmpty()) {
            errors.add(new FieldError("salaryCurrency", "is required"));
        } else if (draft.country() != null && !draft.country().currency().getCurrencyCode().equals(code)) {
            errors.add(new FieldError(
                    "salaryCurrency", "must be " + draft.country().currency().getCurrencyCode() + " for " + draft.country().displayName()));
        } else if (draft.country() != null) {
            currency = draft.country().currency();
        }

        LocalDate start = EmployeeDraft.date("salaryEffectiveStart", salary.effectiveStart(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new NewEmployee(draft, amount, currency, start);
    }
}
