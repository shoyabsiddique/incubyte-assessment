package com.acme.salary.employees.persistence;

import com.acme.salary.employees.domain.DirectoryQuery;
import com.acme.salary.employees.domain.SortDirection;
import com.acme.salary.employees.domain.SortField;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** The directory's database access: pages for ordinary sorts, ids only for salary ranking. */
@Repository
public class EmployeeDirectoryStore {

    private final EmployeeRepository employees;
    private final EntityManager entityManager;

    EmployeeDirectoryStore(EmployeeRepository employees, EntityManager entityManager) {
        this.employees = employees;
        this.entityManager = entityManager;
    }

    /** A page sorted by the database; ties are broken by Employee ID so paging is stable. */
    public Page<EmployeeEntity> page(DirectoryQuery query) {
        Sort.Direction direction = query.direction() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = switch (query.sortField()) {
            case NAME -> Sort.by(direction, "lastName", "firstName");
            case JOB_TITLE -> Sort.by(direction, "jobTitle");
            case DEPARTMENT -> Sort.by(direction, "department");
            case COUNTRY -> Sort.by(direction, "country");
            case HIRE_DATE -> Sort.by(direction, "hireDate");
            case STATUS -> Sort.by(direction, "status");
            case EMPLOYEE_ID, SALARY -> Sort.by(direction, "employeeId");
        };
        if (query.sortField() != SortField.EMPLOYEE_ID && query.sortField() != SortField.SALARY) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "employeeId"));
        }
        return employees.findAll(EmployeeSpecifications.matching(query), PageRequest.of(query.page(), query.size(), sort));
    }

    /** Ids of everyone matching, in Employee ID order, for ranking by salary. */
    public List<UUID> matchingIds(DirectoryQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> criteria = cb.createQuery(UUID.class);
        Root<EmployeeEntity> root = criteria.from(EmployeeEntity.class);
        criteria.select(root.get("id"))
                .where(EmployeeSpecifications.matching(query).toPredicate(root, criteria, cb))
                .orderBy(cb.asc(root.get("employeeId")));
        return entityManager.createQuery(criteria).getResultList();
    }

    public java.util.Optional<EmployeeEntity> byId(UUID id) {
        return employees.findById(id);
    }

    /** The given employees in the order of {@code ids}. */
    public List<EmployeeEntity> byIdsInOrder(List<UUID> ids) {
        Map<UUID, EmployeeEntity> found = employees.findAllById(ids).stream()
                .collect(Collectors.toMap(EmployeeEntity::getId, Function.identity()));
        // someone removed since the ranking was made is simply left out
        return ids.stream().map(found::get).filter(java.util.Objects::nonNull).toList();
    }

    public List<String> departments() {
        return employees.distinctDepartments();
    }

    public List<String> jobTitles() {
        return employees.distinctJobTitles();
    }
}
