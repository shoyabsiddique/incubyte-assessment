package com.acme.salary.analytics.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Read-only access to the analytics projection: no save or delete exists to call. */
public interface ActiveCompensationRepository
        extends Repository<ActiveCompensationEntity, UUID>, JpaSpecificationExecutor<ActiveCompensationEntity> {

    @Query("select distinct c.department from ActiveCompensationEntity c order by c.department")
    List<String> distinctDepartments();

    @Query("select distinct c.jobTitle from ActiveCompensationEntity c order by c.jobTitle")
    List<String> distinctJobTitles();
}
