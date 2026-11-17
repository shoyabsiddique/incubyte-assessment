package com.acme.salary.compensation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the exchange-rate migration, the entity mapping (the application only starts if
 * Hibernate validates the schema) and that the snapshot reaches the domain intact.
 */
class ExchangeRateSnapshotIT extends AbstractMariaDbIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");

    @Autowired
    private ExchangeRateQuery exchangeRates;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theMigrationSeedsOneRateForEverySupportedCurrency() {
        ExchangeRates snapshot = exchangeRates.snapshot();

        assertThat(snapshot.currencies()).containsExactlyInAnyOrderElementsOf(Country.supportedCurrencies());
        assertThat(snapshot.rateToUsd(USD)).isEqualByComparingTo("1");
    }

    @Test
    void everySeededRateIsPositive() {
        Integer nonPositive = jdbc.queryForObject("SELECT COUNT(*) FROM exchange_rate WHERE rate_to_usd <= 0", Integer.class);

        assertThat(nonPositive).isZero();
    }

    @Test
    void theSnapshotConvertsUsingTheStoredRate() {
        BigDecimal stored =
                jdbc.queryForObject("SELECT rate_to_usd FROM exchange_rate WHERE currency = 'INR'", BigDecimal.class);

        Money usd = exchangeRates.snapshot().toUsd(Money.of("100", INR));

        assertThat(usd.currency()).isEqualTo(USD);
        assertThat(usd.amount()).isEqualByComparingTo(stored.multiply(new BigDecimal("100")));
    }

    @Test
    void ratesAreStoredWithEightDecimalPlaces() {
        Set<String> columns = Set.copyOf(jdbc.queryForList(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'exchange_rate' AND COLUMN_NAME = 'rate_to_usd'",
                String.class));

        assertThat(columns).containsExactly("decimal(19,8)");
    }

    @Test
    void theDatabaseRefusesANonPositiveRate() {
        try {
            assertThatThrownBy(() -> jdbc.update(
                            "INSERT INTO exchange_rate (currency, rate_to_usd, version) VALUES ('XXX', 0, 0)"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM exchange_rate WHERE currency = 'XXX'");
        }
    }

    @Test
    void theDatabaseRefusesAUsdRateThatIsNotOne() {
        try {
            assertThatThrownBy(() -> jdbc.update("UPDATE exchange_rate SET rate_to_usd = 1.1 WHERE currency = 'USD'"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbc.update("UPDATE exchange_rate SET rate_to_usd = 1 WHERE currency = 'USD'");
        }
    }
}
