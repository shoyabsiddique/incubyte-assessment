package com.acme.salary.employees.persistence;

import com.acme.salary.employees.domain.DirectoryQuery;
import com.acme.salary.employees.domain.SearchTerms;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class EmployeeSpecifications {

    private EmployeeSpecifications() {}

    /** Every search word must match somewhere, and every filter must hold. */
    static Specification<EmployeeEntity> matching(DirectoryQuery query) {
        return (root, ignored, cb) -> {
            List<Predicate> all = new ArrayList<>();
            for (String token : query.searchTokens()) {
                String pattern = SearchTerms.likePattern(token);
                all.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(root.get("lastName")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(root.get("workEmail")), pattern, SearchTerms.ESCAPE),
                        cb.like(cb.lower(root.get("employeeId")), pattern, SearchTerms.ESCAPE)));
            }
            query.country().ifPresent(value -> all.add(cb.equal(root.get("country"), value)));
            query.department().ifPresent(value -> all.add(cb.equal(root.get("department"), value)));
            query.jobTitle().ifPresent(value -> all.add(cb.equal(root.get("jobTitle"), value)));
            query.employmentType().ifPresent(value -> all.add(cb.equal(root.get("employmentType"), value)));
            query.status().ifPresent(value -> all.add(cb.equal(root.get("status"), value)));
            return cb.and(all.toArray(Predicate[]::new));
        };
    }
}
