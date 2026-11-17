package com.acme.salary.employees.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, UUID>, JpaSpecificationExecutor<EmployeeEntity> {

    boolean existsByWorkEmailIgnoreCase(String workEmail);

    boolean existsByWorkEmailIgnoreCaseAndIdNot(String workEmail, UUID id);

    @Query("select distinct e.department from EmployeeEntity e order by e.department")
    List<String> distinctDepartments();

    @Query("select distinct e.jobTitle from EmployeeEntity e order by e.jobTitle")
    List<String> distinctJobTitles();
}
