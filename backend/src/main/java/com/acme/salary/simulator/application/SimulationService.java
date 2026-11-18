package com.acme.salary.simulator.application;

import com.acme.salary.analytics.api.ActivePayQuery;
import com.acme.salary.analytics.api.UsdPay;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import com.acme.salary.simulator.domain.IncrementSimulator;
import com.acme.salary.simulator.domain.SimulationResult;
import com.acme.salary.simulator.domain.SimulationScope;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs a what-if increment. Read-only by construction: it only ever reads the shared projection. */
@Service
@Transactional(readOnly = true)
public class SimulationService {

    private static final Pattern PERCENT = Pattern.compile("^\\d{1,3}(\\.\\d{1,4})?$");

    private final ActivePayQuery pay;

    SimulationService(ActivePayQuery pay) {
        this.pay = pay;
    }

    public SimulationOutcome simulate(String percentText, String scopeText, String value) {
        List<FieldError> errors = new ArrayList<>();
        BigDecimal percent = parsePercent(percentText, errors);
        SimulationScope.Type type = parseType(scopeText, errors);
        List<UsdPay> people = pay.activePay();
        SimulationScope scope = type == null ? null : scopeOf(type, value, people, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new SimulationOutcome(scope, percent, IncrementSimulator.simulate(people, percent, scope));
    }

    private static BigDecimal parsePercent(String text, List<FieldError> errors) {
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            errors.add(new FieldError("percent", "is required"));
            return null;
        }
        if (!PERCENT.matcher(raw).matches() || new BigDecimal(raw).compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add(new FieldError("percent", "must be a number from 0 to 100"));
            return null;
        }
        return new BigDecimal(raw);
    }

    private static SimulationScope.Type parseType(String text, List<FieldError> errors) {
        try {
            return SimulationScope.Type.valueOf(text == null ? "" : text.trim());
        } catch (IllegalArgumentException unknown) {
            errors.add(new FieldError("scope", "must be ORGANIZATION, COUNTRY or DEPARTMENT"));
            return null;
        }
    }

    private static SimulationScope scopeOf(
            SimulationScope.Type type, String value, List<UsdPay> people, List<FieldError> errors) {
        String trimmed = value == null ? "" : value.trim();
        switch (type) {
            case ORGANIZATION:
                return SimulationScope.organization();
            case COUNTRY:
                if (trimmed.isEmpty()) {
                    errors.add(new FieldError("value", "is required for a country scope"));
                    return null;
                }
                try {
                    return SimulationScope.country(Country.valueOf(trimmed));
                } catch (IllegalArgumentException unknown) {
                    errors.add(new FieldError("value", "is not a supported country"));
                    return null;
                }
            default:
                if (trimmed.isEmpty()) {
                    errors.add(new FieldError("value", "is required for a department scope"));
                    return null;
                }
                if (people.stream().noneMatch(p -> p.department().equals(trimmed))) {
                    errors.add(new FieldError("value", "is not a known department"));
                    return null;
                }
                return SimulationScope.department(trimmed);
        }
    }
}
