package com.acme.salary.seed.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SeedGeneratorTest {

    private static final long SEED = 20261117L;
    private static final ExchangeRates RATES = ExchangeRates.of(Map.of(
            Currency.getInstance("INR"), new BigDecimal("0.012"),
            Currency.getInstance("GBP"), new BigDecimal("1.27"),
            Currency.getInstance("EUR"), new BigDecimal("1.08"),
            Currency.getInstance("SGD"), new BigDecimal("0.74"),
            Currency.getInstance("AED"), new BigDecimal("0.2723"),
            Currency.getInstance("CAD"), new BigDecimal("0.73"),
            Currency.getInstance("AUD"), new BigDecimal("0.65")));

    private static final String GOLDEN_DIGEST = "e3eecda595598899ed07441053b7e93b6bd5771b638f216baf44261e2dc19aca";
    private static final String FIRST_EMPLOYEE_ID = "c27e0a55-3b72-3668-9fbc-cee3f51b9a75";
    private static final String FIRST_EMPLOYEE_EMAIL = "charlotte.smith@acme.example";
    private static final Country FIRST_EMPLOYEE_COUNTRY = Country.AUSTRALIA;

    private static final List<SeedEmployee> DATA = SeedGenerator.generate(SEED, RATES);

    @Test
    void generatesExactlyTenThousandEmployees() {
        assertThat(DATA).hasSize(10_000);
    }

    @Test
    void headcountPerCountryMatchesTheBrief() {
        Map<Country, Long> counts = DATA.stream().collect(Collectors.groupingBy(SeedEmployee::country, Collectors.counting()));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(
                Country.INDIA, 3500L,
                Country.USA, 2500L,
                Country.UK, 1000L,
                Country.GERMANY, 800L,
                Country.SINGAPORE, 700L,
                Country.UAE, 600L,
                Country.CANADA, 500L,
                Country.AUSTRALIA, 400L));
    }

    @Test
    void everySalaryIsPaidInTheCurrencyOfTheEmployeesCountry() {
        for (SeedEmployee employee : DATA) {
            for (SeedCompensation record : employee.compensation()) {
                assertThat(record.currency()).isEqualTo(employee.country().currency());
            }
        }
    }

    @Test
    void employeeIdsAreSequentialFromOneToTenThousand() {
        List<String> ids = DATA.stream().map(SeedEmployee::employeeId).sorted().toList();

        assertThat(ids.get(0)).isEqualTo("EMP-000001");
        assertThat(ids.get(9_999)).isEqualTo("EMP-010000");
        assertThat(new HashSet<>(ids)).hasSize(10_000);
    }

    @Test
    void workEmailsAreUniqueIgnoringCaseAndLookLikeCompanyAddresses() {
        Set<String> emails = new HashSet<>();
        for (SeedEmployee employee : DATA) {
            String email = employee.workEmail();
            assertThat(email).endsWith("@acme.example").doesNotContain(" ").isEqualTo(email.toLowerCase());
            assertThat(emails.add(email)).as(email).isTrue();
        }
    }

    @Test
    void identifiersAreUnique() {
        Set<java.util.UUID> ids = new HashSet<>();
        DATA.forEach(e -> {
            assertThat(ids.add(e.id())).isTrue();
            e.compensation().forEach(r -> assertThat(ids.add(r.id())).isTrue());
        });
    }

    @Test
    void usesEightDepartmentsAndTwentyFiveJobTitlesEachBelongingToOneDepartment() {
        assertThat(SeedCatalog.departments()).hasSize(8);
        assertThat(SeedCatalog.jobTitles()).hasSize(25);

        Map<String, Set<String>> departmentsByTitle = DATA.stream().collect(
                Collectors.groupingBy(SeedEmployee::jobTitle, Collectors.mapping(SeedEmployee::department, Collectors.toSet())));

        assertThat(departmentsByTitle).hasSize(25);
        departmentsByTitle.forEach((title, departments) -> assertThat(departments).hasSize(1));
        assertThat(DATA.stream().map(SeedEmployee::department).collect(Collectors.toSet())).hasSize(8);
    }

    @Test
    void mostEmployeesAreActiveButOtherStatusesAreRepresented() {
        Map<EmploymentStatus, Long> byStatus =
                DATA.stream().collect(Collectors.groupingBy(SeedEmployee::status, Collectors.counting()));

        assertThat(byStatus.get(EmploymentStatus.ACTIVE)).isBetween(9_000L, 9_500L);
        assertThat(byStatus.get(EmploymentStatus.ON_LEAVE)).isGreaterThan(100L);
        assertThat(byStatus.get(EmploymentStatus.TERMINATED)).isGreaterThan(200L);
    }

    @Test
    void allEmploymentTypesAreRepresented() {
        assertThat(DATA.stream().map(SeedEmployee::employmentType).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder(EmploymentType.values());
    }

    @Test
    void everyEmployeeHasExactlyOneOpenCurrentRecord() {
        for (SeedEmployee employee : DATA) {
            long open = employee.compensation().stream().filter(r -> r.effectiveEnd() == null).count();
            assertThat(open).as(employee.employeeId()).isEqualTo(1);
        }
    }

    @Test
    void salaryHistoryIsAGaplessChainStartingAtHireAndEndingOpen() {
        for (SeedEmployee employee : DATA) {
            List<SeedCompensation> chain = employee.compensation().stream()
                    .sorted(java.util.Comparator.comparing(SeedCompensation::effectiveStart))
                    .toList();

            assertThat(chain.get(0).effectiveStart()).as(employee.employeeId()).isEqualTo(employee.hireDate());
            for (int i = 0; i < chain.size() - 1; i++) {
                assertThat(chain.get(i).effectiveEnd())
                        .as(employee.employeeId())
                        .isEqualTo(chain.get(i + 1).effectiveStart().minusDays(1));
                assertThat(chain.get(i).effectiveEnd()).isAfterOrEqualTo(chain.get(i).effectiveStart());
            }
            assertThat(chain.get(chain.size() - 1).effectiveEnd()).isNull();
            assertThat(chain.get(chain.size() - 1).effectiveStart()).isBeforeOrEqualTo(SeedGenerator.BASE_DATE);
        }
    }

    @Test
    void aboutOneInSevenEmployeesHasAnEarlierSalary() {
        long withHistory = DATA.stream().filter(e -> e.compensation().size() > 1).count();
        int longest = DATA.stream().mapToInt(e -> e.compensation().size()).max().orElse(0);

        assertThat(withHistory).isBetween(1_000L, 2_000L);
        assertThat(longest).isBetween(2, 4);
    }

    @Test
    void salariesRiseOverTimeAndAreExactTwoDecimalAmounts() {
        for (SeedEmployee employee : DATA) {
            List<SeedCompensation> chain = employee.compensation().stream()
                    .sorted(java.util.Comparator.comparing(SeedCompensation::effectiveStart))
                    .toList();
            for (int i = 0; i < chain.size(); i++) {
                assertThat(chain.get(i).amount().signum()).isPositive();
                assertThat(chain.get(i).amount().scale()).isEqualTo(2);
                if (i > 0) {
                    assertThat(chain.get(i).amount()).isGreaterThan(chain.get(i - 1).amount());
                }
            }
        }
    }

    @Test
    void hireDatesFallBetween2010AndTheBaseDate() {
        for (SeedEmployee employee : DATA) {
            assertThat(employee.hireDate()).isBetween(LocalDate.of(2010, 1, 1), SeedGenerator.BASE_DATE.minusDays(30));
        }
    }

    @Test
    void payLevelsAreRealisticAndCheaperCountriesPayLess() {
        double usa = averageUsd("Software Engineer", Country.USA);
        double india = averageUsd("Software Engineer", Country.INDIA);

        assertThat(usa).isBetween(85_000.0, 125_000.0);
        assertThat(india / usa).isBetween(0.2, 0.45);
    }

    /**
     * Pinned to a fixed value so a change in output between JVM launches fails the build. Two runs
     * inside one JVM (the other determinism tests) cannot see order that varies per launch.
     */
    @Test
    void producesTheSameDataInEveryJvmLaunch() throws Exception {
        assertThat(digestOf(DATA)).isEqualTo(GOLDEN_DIGEST);
    }

    @Test
    void theFirstEmployeeIsPinnedToKnownValues() {
        SeedEmployee first = DATA.stream().filter(e -> e.employeeId().equals("EMP-000001")).findFirst().orElseThrow();

        assertThat(first.id()).hasToString(FIRST_EMPLOYEE_ID);
        assertThat(first.workEmail()).isEqualTo(FIRST_EMPLOYEE_EMAIL);
        assertThat(first.country()).isEqualTo(FIRST_EMPLOYEE_COUNTRY);
    }

    private static String digestOf(List<SeedEmployee> employees) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (SeedEmployee e : employees) {
            String row = String.join("|", e.id().toString(), e.employeeId(), e.firstName(), e.lastName(), e.workEmail(),
                    e.jobTitle(), e.department(), e.country().name(), e.city(), e.employmentType().name(),
                    e.hireDate().toString(), e.status().name());
            digest.update(row.getBytes(StandardCharsets.UTF_8));
            for (SeedCompensation c : e.compensation()) {
                String record = String.join("|", c.id().toString(), c.amount().toPlainString(),
                        c.currency().getCurrencyCode(), c.effectiveStart().toString(), String.valueOf(c.effectiveEnd()));
                digest.update(record.getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void sameSeedGivesIdenticalDataAndAnotherSeedDoesNot() {
        List<SeedEmployee> again = SeedGenerator.generate(SEED, RATES);
        List<SeedEmployee> other = SeedGenerator.generate(SEED + 1, RATES);

        assertThat(again).isEqualTo(DATA);
        assertThat(other).hasSize(10_000).isNotEqualTo(DATA);
    }

    @Test
    void exactlySixteenDeliberateOutliersStandOutAndTheNaturalSpreadFlagsNobodyElse() {
        assertThat(flaggedByTheTwentyPercentRule()).isEqualTo(16);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static double averageUsd(String title, Country country) {
        return DATA.stream()
                .filter(e -> e.jobTitle().equals(title) && e.country() == country && e.status() == EmploymentStatus.ACTIVE)
                .mapToDouble(e -> currentAmount(e).multiply(rate(country)).doubleValue())
                .average()
                .orElseThrow();
    }

    private static BigDecimal rate(Country country) {
        return RATES.rateToUsd(country.currency());
    }

    private static BigDecimal currentAmount(SeedEmployee employee) {
        return employee.compensation().stream()
                .filter(r -> r.effectiveEnd() == null)
                .findFirst()
                .orElseThrow()
                .amount();
    }

    /** The PRD rule: more than 20% from the peer-group median, groups of at least five, active staff. */
    private static int flaggedByTheTwentyPercentRule() {
        Map<String, List<SeedEmployee>> groups = new TreeMap<>(DATA.stream()
                .filter(e -> e.status() == EmploymentStatus.ACTIVE)
                .collect(Collectors.groupingBy(e -> e.country() + "|" + e.department() + "|" + e.jobTitle())));
        int flagged = 0;
        for (List<SeedEmployee> group : groups.values()) {
            if (group.size() < 5) {
                continue;
            }
            List<BigDecimal> usd = new ArrayList<>();
            group.forEach(e -> usd.add(currentAmount(e).multiply(rate(e.country()))));
            usd.sort(BigDecimal::compareTo);
            BigDecimal median = usd.size() % 2 == 1
                    ? usd.get(usd.size() / 2)
                    : usd.get(usd.size() / 2 - 1).add(usd.get(usd.size() / 2)).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
            for (BigDecimal value : usd) {
                BigDecimal deviation = value.subtract(median).abs().divide(median, 10, RoundingMode.HALF_UP);
                if (deviation.compareTo(new BigDecimal("0.20")) > 0) {
                    flagged++;
                }
            }
        }
        return flagged;
    }
}
