package com.acme.salary.analytics.persistence;

import com.acme.salary.analytics.domain.ActiveSalary;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the {@code v_active_compensation} view: the one place analytics gets its rows from. */
@Repository
public class ActiveCompensationStore {

    private final JdbcClient jdbc;

    ActiveCompensationStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The salary each active employee is paid on {@code today}, narrowed by the filters that are present. */
    public List<ActiveSalary> findInForce(
            LocalDate today, Optional<Country> country, Optional<String> department, Optional<String> jobTitle) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, employee_id, first_name, last_name, country, department, job_title, amount, currency "
                        + "FROM v_active_compensation WHERE effective_start <= :today "
                        + "AND (effective_end IS NULL OR effective_end >= :today)");
        List<Object[]> named = new ArrayList<>();
        country.ifPresent(c -> {
            sql.append(" AND country = :country");
            named.add(new Object[] {"country", c.name()});
        });
        department.ifPresent(d -> {
            sql.append(" AND department = :department");
            named.add(new Object[] {"department", d});
        });
        jobTitle.ifPresent(t -> {
            sql.append(" AND job_title = :jobTitle");
            named.add(new Object[] {"jobTitle", t});
        });
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString()).param("today", today);
        for (Object[] parameter : named) {
            statement = statement.param((String) parameter[0], parameter[1]);
        }
        return statement
                .query((rs, row) -> new ActiveSalary(
                        rs.getString("id"),
                        rs.getString("employee_id"),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        Country.valueOf(rs.getString("country")),
                        rs.getString("department"),
                        rs.getString("job_title"),
                        Money.of(rs.getBigDecimal("amount"), Currency.getInstance(rs.getString("currency")))))
                .list();
    }

    public TreeSet<String> departments() {
        return new TreeSet<>(jdbc.sql("SELECT DISTINCT department FROM v_active_compensation")
                .query(String.class)
                .list());
    }

    public TreeSet<String> jobTitles() {
        return new TreeSet<>(jdbc.sql("SELECT DISTINCT job_title FROM v_active_compensation")
                .query(String.class)
                .list());
    }
}
