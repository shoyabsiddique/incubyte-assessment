package com.acme.salary.analytics.persistence;

import com.acme.salary.analytics.domain.ActiveSalary;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** Turns the analytics view into domain rows, with every filter expressed through JPA specifications. */
@Repository
public class ActiveCompensationStore {

    private final ActiveCompensationRepository repository;

    ActiveCompensationStore(ActiveCompensationRepository repository) {
        this.repository = repository;
    }

    /** The salary each active employee is paid on {@code today}, narrowed by the filters that are present. */
    public List<ActiveSalary> findInForce(
            LocalDate today, Optional<Country> country, Optional<String> department, Optional<String> jobTitle) {
        Specification<ActiveCompensationEntity> spec = (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("effectiveStart"), today),
                cb.or(cb.isNull(root.get("effectiveEnd")), cb.greaterThanOrEqualTo(root.get("effectiveEnd"), today)));
        if (country.isPresent()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("country"), country.get().name()));
        }
        if (department.isPresent()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("department"), department.get()));
        }
        if (jobTitle.isPresent()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("jobTitle"), jobTitle.get()));
        }
        return repository.findAll(spec).stream().map(ActiveCompensationStore::toDomain).toList();
    }

    public TreeSet<String> departments() {
        return new TreeSet<>(repository.distinctDepartments());
    }

    public TreeSet<String> jobTitles() {
        return new TreeSet<>(repository.distinctJobTitles());
    }

    private static ActiveSalary toDomain(ActiveCompensationEntity row) {
        return new ActiveSalary(
                row.getEmployeeUuid().toString(),
                row.getEmployeeId(),
                row.getFirstName() + " " + row.getLastName(),
                Country.valueOf(row.getCountry()),
                row.getDepartment(),
                row.getJobTitle(),
                Money.of(row.getAmount(), Currency.getInstance(row.getCurrency())));
    }
}
