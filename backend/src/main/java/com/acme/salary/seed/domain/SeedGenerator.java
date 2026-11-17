package com.acme.salary.seed.domain;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.seed.domain.SeedCatalog.Title;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.MoneyMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Generates the demo workforce. Pure and deterministic: the same seed and rates always give the
 * same people, ids and salaries, so the data can be reproduced anywhere and tested exactly.
 */
public final class SeedGenerator {

    /** Fixed so the data never depends on the day the seed is run. */
    public static final LocalDate BASE_DATE = LocalDate.of(2026, 11, 1);

    static final Map<Country, Integer> HEADCOUNT = headcount();

    private static final LocalDate EARLIEST_HIRE = LocalDate.of(2010, 1, 1);
    private static final int DELIBERATE_OUTLIERS = 16;
    private static final int OUTLIER_PEER_GROUP_MINIMUM = 8;

    private SeedGenerator() {}

    public static List<SeedEmployee> generate(long seed, ExchangeRates rates) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Country> order = shuffledCountries(random);
        int totalWeight = SeedCatalog.titles().stream().mapToInt(Title::weight).sum();

        List<Draft> drafts = new ArrayList<>(order.size());
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            drafts.add(draft(i + 1, seed, order.get(i), random, totalWeight, rates, emails));
        }

        addDeliberateOutliers(drafts, random);

        List<SeedEmployee> result = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            result.add(draft.toEmployee());
        }
        return List.copyOf(result);
    }

    // ---------------------------------------------------------------------------------------------

    private static Draft draft(
            int number,
            long seed,
            Country country,
            SplittableRandom random,
            int totalWeight,
            ExchangeRates rates,
            Set<String> emails) {
        Draft d = new Draft();
        d.number = number;
        d.id = uuid("employee:" + seed + ":" + number);
        d.employeeId = String.format(Locale.ROOT, "EMP-%06d", number);
        d.country = country;

        String first = pick(SeedCatalog.firstNames(country), random);
        String last = pick(SeedCatalog.lastNames(country), random);
        d.firstName = first;
        d.lastName = last;
        d.workEmail = uniqueEmail(first, last, number, emails);
        d.city = pick(SeedCatalog.cities(country), random);

        Title title = pickTitle(random, totalWeight);
        d.jobTitle = title.name();
        d.department = title.department();

        double typeRoll = random.nextDouble();
        d.employmentType = typeRoll < 0.85 ? EmploymentType.FULL_TIME : typeRoll < 0.90 ? EmploymentType.PART_TIME : EmploymentType.CONTRACT;
        double statusRoll = random.nextDouble();
        d.status = statusRoll < 0.92 ? EmploymentStatus.ACTIVE : statusRoll < 0.95 ? EmploymentStatus.ON_LEAVE : EmploymentStatus.TERMINATED;

        long span = ChronoUnit.DAYS.between(EARLIEST_HIRE, BASE_DATE.minusDays(30));
        d.hireDate = EARLIEST_HIRE.plusDays((long) (span * Math.sqrt(random.nextDouble())));

        BigDecimal currentAmount = currentSalary(title, country, d.hireDate, random, rates);
        buildHistory(d, currentAmount, random, seed);
        return d;
    }

    private static BigDecimal currentSalary(
            Title title, Country country, LocalDate hireDate, SplittableRandom random, ExchangeRates rates) {
        long tenureYears = Math.min(10, ChronoUnit.YEARS.between(hireDate, BASE_DATE));
        BigDecimal tenure = BigDecimal.ONE.add(BigDecimal.valueOf(tenureYears).multiply(new BigDecimal("0.01")));
        BigDecimal jitter = BigDecimal.valueOf(0.92 + random.nextDouble() * 0.16);
        BigDecimal usd = BigDecimal.valueOf(title.baseUsd())
                .multiply(SeedCatalog.payLevel(country), MoneyMath.CONTEXT)
                .multiply(tenure, MoneyMath.CONTEXT)
                .multiply(jitter, MoneyMath.CONTEXT);
        BigDecimal local = usd.divide(rates.rateToUsd(country.currency()), MoneyMath.CONTEXT);
        return roundToHundreds(local);
    }

    /** About 15% of people have earlier salaries: 1 to 3 raises of 4% to 10% at even intervals. */
    private static void buildHistory(Draft d, BigDecimal currentAmount, SplittableRandom random, long seed) {
        int earlier = 0;
        if (random.nextDouble() < 0.15) {
            double roll = random.nextDouble();
            earlier = roll < 0.60 ? 1 : roll < 0.90 ? 2 : 3;
        }
        long available = ChronoUnit.DAYS.between(d.hireDate, BASE_DATE);
        earlier = (int) Math.min(earlier, available / 400);

        List<LocalDate> starts = new ArrayList<>();
        starts.add(d.hireDate);
        for (int i = 1; i <= earlier; i++) {
            starts.add(d.hireDate.plusDays(available * i / (earlier + 1)));
        }

        List<BigDecimal> amounts = new ArrayList<>(Collections.nCopies(starts.size(), currentAmount));
        for (int i = starts.size() - 2; i >= 0; i--) {
            BigDecimal raise = BigDecimal.valueOf(1.04 + random.nextDouble() * 0.06);
            BigDecimal earlierAmount = roundToHundreds(amounts.get(i + 1).divide(raise, MoneyMath.CONTEXT));
            if (earlierAmount.signum() <= 0 || earlierAmount.compareTo(amounts.get(i + 1)) >= 0) {
                throw new IllegalStateException("Cannot build a rising salary history for employee " + d.number);
            }
            amounts.set(i, earlierAmount);
        }

        for (int i = 0; i < starts.size(); i++) {
            LocalDate end = i == starts.size() - 1 ? null : starts.get(i + 1).minusDays(1);
            d.records.add(new SeedCompensation(
                    uuid("compensation:" + seed + ":" + d.number + ":" + i),
                    amounts.get(i),
                    d.country.currency(),
                    starts.get(i),
                    end));
        }
    }

    /** Picks active people in large peer groups and moves their pay well outside the group's range. */
    private static void addDeliberateOutliers(List<Draft> drafts, SplittableRandom random) {
        Map<String, List<Draft>> groups = new TreeMap<>();
        for (Draft d : drafts) {
            if (d.status == EmploymentStatus.ACTIVE) {
                groups.computeIfAbsent(d.country + "|" + d.department + "|" + d.jobTitle, k -> new ArrayList<>()).add(d);
            }
        }
        List<String> eligible = new ArrayList<>();
        groups.forEach((key, members) -> {
            if (members.size() >= OUTLIER_PEER_GROUP_MINIMUM) {
                eligible.add(key);
            }
        });
        shuffle(eligible, random);

        int placed = 0;
        for (String key : eligible) {
            if (placed == DELIBERATE_OUTLIERS) {
                break;
            }
            Draft target = groups.get(key).stream().filter(d -> d.records.size() == 1).findFirst().orElse(null);
            if (target == null) {
                continue;
            }
            BigDecimal factor = placed < DELIBERATE_OUTLIERS / 2 ? new BigDecimal("1.45") : new BigDecimal("0.65");
            SeedCompensation only = target.records.get(0);
            target.records.set(0, new SeedCompensation(
                    only.id(),
                    roundToHundreds(only.amount().multiply(factor, MoneyMath.CONTEXT)),
                    only.currency(),
                    only.effectiveStart(),
                    only.effectiveEnd()));
            placed++;
        }
        if (placed < DELIBERATE_OUTLIERS) {
            throw new IllegalStateException("Only " + placed + " of " + DELIBERATE_OUTLIERS + " deliberate outliers could be placed");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static List<Country> shuffledCountries(SplittableRandom random) {
        List<Country> order = new ArrayList<>();
        HEADCOUNT.forEach((country, count) -> {
            for (int i = 0; i < count; i++) {
                order.add(country);
            }
        });
        shuffle(order, random);
        return order;
    }

    private static <T> void shuffle(List<T> list, SplittableRandom random) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, random.nextInt(i + 1));
        }
    }

    private static Title pickTitle(SplittableRandom random, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        for (Title title : SeedCatalog.titles()) {
            roll -= title.weight();
            if (roll < 0) {
                return title;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static String pick(List<String> values, SplittableRandom random) {
        return values.get(random.nextInt(values.size()));
    }

    private static String uniqueEmail(String first, String last, int number, Set<String> used) {
        String base = (first + "." + last).toLowerCase(Locale.ROOT);
        String email = base + "@acme.example";
        int suffix = number;
        while (!used.add(email)) {
            email = base + suffix + "@acme.example";
            suffix++;
        }
        return email;
    }

    private static BigDecimal roundToHundreds(BigDecimal amount) {
        return amount.setScale(-2, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<Country, Integer> headcount() {
        Map<Country, Integer> counts = new LinkedHashMap<>();
        counts.put(Country.INDIA, 3_500);
        counts.put(Country.USA, 2_500);
        counts.put(Country.UK, 1_000);
        counts.put(Country.GERMANY, 800);
        counts.put(Country.SINGAPORE, 700);
        counts.put(Country.UAE, 600);
        counts.put(Country.CANADA, 500);
        counts.put(Country.AUSTRALIA, 400);
        // Keep insertion order: Map.copyOf would iterate in an order that changes with every JVM launch.
        return Collections.unmodifiableMap(counts);
    }

    /** Mutable working copy used while generating; turned into an immutable record at the end. */
    private static final class Draft {
        int number;
        UUID id;
        String employeeId;
        String firstName;
        String lastName;
        String workEmail;
        String jobTitle;
        String department;
        Country country;
        String city;
        EmploymentType employmentType;
        LocalDate hireDate;
        EmploymentStatus status;
        final List<SeedCompensation> records = new ArrayList<>();

        SeedEmployee toEmployee() {
            return new SeedEmployee(
                    id, employeeId, firstName, lastName, workEmail, jobTitle, department, country, city,
                    employmentType, hireDate, status, List.copyOf(records));
        }
    }
}
