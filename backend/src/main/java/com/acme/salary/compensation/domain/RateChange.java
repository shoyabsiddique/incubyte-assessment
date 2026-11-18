package com.acme.salary.compensation.domain;

import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A valid edit of one exchange rate: a supported, non-USD currency, a positive rate the table can
 * hold exactly (11 digits before the point, 8 after) and the version the client based it on.
 */
public record RateChange(Currency currency, BigDecimal rate, long version) {

    private static final Pattern RATE = Pattern.compile("^\\d{1,11}(\\.\\d{1,8})?$");
    private static final String USD = "USD";

    public static RateChange parse(String currencyCode, String rateText, Long version) {
        Currency currency = supported(currencyCode);
        if (currency.getCurrencyCode().equals(USD)) {
            throw new DomainException(ErrorKind.INVALID_INPUT, "USD_RATE_FIXED", "The USD rate is fixed at 1 and can not be edited");
        }
        List<FieldError> errors = new ArrayList<>();
        BigDecimal rate = null;
        String raw = rateText == null ? "" : rateText.trim();
        if (raw.isEmpty()) {
            errors.add(new FieldError("rate", "is required"));
        } else if (!RATE.matcher(raw).matches() || new BigDecimal(raw).signum() <= 0) {
            errors.add(new FieldError("rate", "must be a number greater than 0 with at most 8 decimals"));
        } else {
            rate = new BigDecimal(raw);
        }
        if (version == null || version < 0) {
            errors.add(new FieldError("version", "is required"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new RateChange(currency, rate, version);
    }

    private static Currency supported(String code) {
        return Country.supportedCurrencies().stream()
                .filter(currency -> currency.getCurrencyCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        ErrorKind.INVALID_INPUT, "UNSUPPORTED_CURRENCY", "There is no exchange rate for this currency"));
    }
}
