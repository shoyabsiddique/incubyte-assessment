package com.acme.salary.seed.application;

import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.seed.domain.SeedEmployee;
import com.acme.salary.seed.domain.SeedGenerator;
import com.acme.salary.shared.domain.ExchangeRates;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the 10,000-employee demo workforce when the application runs with the seed profile.
 * It does nothing if employees already exist, and writes everything in one transaction, so a
 * failure leaves the database as it was.
 */
@Component
@Profile("seed")
public class EmployeeSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmployeeSeedRunner.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ExchangeRateQuery rates;
    private final Function<ExchangeRates, List<SeedEmployee>> generator;

    @Autowired
    public EmployeeSeedRunner(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            ExchangeRateQuery rates,
            @Value("${app.seed.random-seed:20261117}") long randomSeed) {
        this(jdbc, new TransactionTemplate(transactions), rates, randomSeed);
    }

    public EmployeeSeedRunner(JdbcTemplate jdbc, TransactionTemplate transaction, ExchangeRateQuery rates, long randomSeed) {
        this(jdbc, transaction, rates, snapshot -> SeedGenerator.generate(randomSeed, snapshot));
    }

    public EmployeeSeedRunner(
            JdbcTemplate jdbc,
            TransactionTemplate transaction,
            ExchangeRateQuery rates,
            Function<ExchangeRates, List<SeedEmployee>> generator) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.rates = rates;
        this.generator = generator;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public SeedOutcome seed() {
        if (alreadyPopulated()) {
            return SeedOutcome.skippedBecausePopulated();
        }

        long started = System.nanoTime();
        List<SeedEmployee> data = generator.apply(rates.snapshot());
        SeedWriter writer = new SeedWriter(jdbc);
        int records;
        try {
            records = Objects.requireNonNull(transaction.execute(status -> writer.write(data)));
        } catch (DataIntegrityViolationException conflict) {
            // Another process may have seeded between our check and our write; that is not an error.
            if (alreadyPopulated()) {
                log.info("Another process seeded the database first; nothing more to do");
                return SeedOutcome.skippedBecausePopulated();
            }
            throw conflict;
        }
        log.info("Seeded {} employees and {} compensation records in {} ms", data.size(), records,
                (System.nanoTime() - started) / 1_000_000);
        return new SeedOutcome(false, data.size(), records);
    }

    private boolean alreadyPopulated() {
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM employee", Long.class);
        if (existing != null && existing > 0) {
            log.info("Seeding skipped: {} employees already exist", existing);
            return true;
        }
        return false;
    }
}
