package com.acme.salary.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CountryTest {

    @Test
    void eachCountryMapsToItsCurrency() {
        Map<Country, String> expected = Map.of(
                Country.INDIA, "INR",
                Country.USA, "USD",
                Country.UK, "GBP",
                Country.GERMANY, "EUR",
                Country.SINGAPORE, "SGD",
                Country.UAE, "AED",
                Country.CANADA, "CAD",
                Country.AUSTRALIA, "AUD");

        expected.forEach((country, code) ->
                assertThat(country.currency()).isEqualTo(Currency.getInstance(code)));
    }

    @Test
    void thereAreExactlyEightSupportedCountries() {
        assertThat(Country.values()).hasSize(8);
    }

    @Test
    void supportedCurrenciesAreThoseOfTheCountries() {
        Set<Currency> fromCountries =
                Arrays.stream(Country.values()).map(Country::currency).collect(Collectors.toSet());

        assertThat(Country.supportedCurrencies()).containsExactlyInAnyOrderElementsOf(fromCountries);
    }

    @Test
    void eachCountryHasADisplayName() {
        assertThat(Country.UK.displayName()).isEqualTo("United Kingdom");
        assertThat(Country.UAE.displayName()).isEqualTo("United Arab Emirates");
        assertThat(Country.USA.displayName()).isEqualTo("United States");
    }

    @Test
    void countriesCanBeFoundByDisplayNameIgnoringCase() {
        assertThat(Country.fromDisplayName("india")).contains(Country.INDIA);
        assertThat(Country.fromDisplayName("  United Kingdom ")).contains(Country.UK);
        assertThat(Country.fromDisplayName("Atlantis")).isEmpty();
    }
}
