package com.acme.salary.employees.application;

import com.acme.salary.compensation.api.CurrentSalaryQuery;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.employees.domain.DirectoryQuery;
import com.acme.salary.employees.domain.SortDirection;
import com.acme.salary.employees.domain.SortField;
import com.acme.salary.employees.persistence.EmployeeDirectoryStore;
import com.acme.salary.employees.persistence.EmployeeEntity;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeDirectoryService {

    private final EmployeeDirectoryStore store;
    private final CurrentSalaryQuery salaries;
    private final ExchangeRateQuery exchangeRates;

    EmployeeDirectoryService(EmployeeDirectoryStore store, CurrentSalaryQuery salaries, ExchangeRateQuery exchangeRates) {
        this.store = store;
        this.salaries = salaries;
        this.exchangeRates = exchangeRates;
    }

    public DirectoryPage search(DirectoryQuery query) {
        ExchangeRates rates = exchangeRates.snapshot();
        if (query.sortField() == SortField.SALARY) {
            return searchBySalary(query, rates);
        }
        Page<EmployeeEntity> page = store.page(query);
        return new DirectoryPage(summaries(page.getContent(), rates), query.page(), query.size(), page.getTotalElements());
    }

    /** Salary lives in another module, so rank everyone matching through its api, then slice the page. */
    private DirectoryPage searchBySalary(DirectoryQuery query, ExchangeRates rates) {
        List<UUID> ranked = salaries.rankByCurrentUsd(store.matchingIds(query), query.direction() == SortDirection.DESC, rates);
        int from = (int) Math.min((long) query.page() * query.size(), ranked.size());
        int to = Math.min(from + query.size(), ranked.size());
        List<EmployeeEntity> pageOfEmployees = store.byIdsInOrder(ranked.subList(from, to));
        return new DirectoryPage(summaries(pageOfEmployees, rates), query.page(), query.size(), ranked.size());
    }

    /** One employee with current pay, or nothing if there is no such person. */
    public java.util.Optional<EmployeeSummary> find(UUID id) {
        return store.byId(id).map(employee -> summaries(List.of(employee), exchangeRates.snapshot()).get(0));
    }

    public FilterOptions filterOptions() {
        return new FilterOptions(
                Arrays.asList(Country.values()),
                store.departments(),
                store.jobTitles(),
                Arrays.asList(EmploymentType.values()),
                Arrays.asList(EmploymentStatus.values()));
    }

    private List<EmployeeSummary> summaries(List<EmployeeEntity> employees, ExchangeRates rates) {
        Map<UUID, Money> current = salaries.currentSalaries(employees.stream().map(EmployeeEntity::getId).toList());
        return employees.stream()
                .map(e -> {
                    Money salary = current.get(e.getId());
                    return new EmployeeSummary(
                            e.getId(), e.getEmployeeId(), e.getFirstName(), e.getLastName(), e.getWorkEmail(),
                            e.getJobTitle(), e.getDepartment(), e.getCountry(), e.getCity(), e.getEmploymentType(),
                            e.getHireDate(), e.getStatus(), salary, salary == null ? null : rates.toUsd(salary));
                })
                .toList();
    }
}
