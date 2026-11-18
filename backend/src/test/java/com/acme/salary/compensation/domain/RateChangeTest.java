package com.acme.salary.compensation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class RateChangeTest {

    @Test
    void parsesAPositiveRateForASupportedCurrency() {
        RateChange change = RateChange.parse("INR", " 0.0125 ", 3L);

        assertThat(change.currency()).isEqualTo(Currency.getInstance("INR"));
        assertThat(change.rate()).isEqualByComparingTo(new BigDecimal("0.0125"));
        assertThat(change.version()).isEqualTo(3L);
    }

    @Test
    void acceptsTheMostPrecisionTheTableStores() {
        assertThat(RateChange.parse("GBP", "1.27000001", 0L).rate()).isEqualByComparingTo("1.27000001");
        assertThat(RateChange.parse("GBP", "99999999999.99999999", 0L).rate()).isEqualByComparingTo("99999999999.99999999");
    }

    @Test
    void rejectsRatesThatAreNotPositiveNumbersTheTableCanStore() {
        for (String bad : new String[] {"0", "0.0", "-1", "abc", "", "  ", null, "1e3", "1,5", "0.123456789", "123456789012"}) {
            ValidationFailedException error =
                    catchThrowableOfType(ValidationFailedException.class, () -> RateChange.parse("INR", bad, 0L));
            assertThat(error).as("rate %s", bad).isNotNull();
            assertThat(error.errors()).as("rate %s", bad).extracting(FieldError::field).containsExactly("rate");
        }
    }

    @Test
    void aMissingVersionIsRejected() {
        ValidationFailedException error =
                catchThrowableOfType(ValidationFailedException.class, () -> RateChange.parse("INR", "0.01", null));

        assertThat(error.errors()).extracting(FieldError::field).containsExactly("version");
    }

    @Test
    void usdIsFixedAtOneAndCanNotBeEdited() {
        DomainException error = catchThrowableOfType(DomainException.class, () -> RateChange.parse("USD", "1", 0L));

        assertThat(error.kind()).isEqualTo(ErrorKind.INVALID_INPUT);
        assertThat(error.code()).isEqualTo("USD_RATE_FIXED");
    }

    @Test
    void anUnknownCurrencyIsRejected() {
        for (String bad : new String[] {"XXX", "ZZZ", "inr", "", "JPY", "TOOLONG"}) {
            DomainException error = catchThrowableOfType(DomainException.class, () -> RateChange.parse(bad, "1", 0L));
            assertThat(error).as("currency %s", bad).isNotNull();
            assertThat(error.kind()).as("currency %s", bad).isEqualTo(ErrorKind.INVALID_INPUT);
            assertThat(error.code()).as("currency %s", bad).isEqualTo("UNSUPPORTED_CURRENCY");
        }
    }
}
