package com.acme.salary.seed.application;

import com.acme.salary.seed.domain.SeedCompensation;
import com.acme.salary.seed.domain.SeedEmployee;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes generated data with multi-row inserts, so thousands of rows cost only dozens of round
 * trips even to a remote database. The seed is the one sanctioned writer that bypasses the
 * application services (and therefore writes no audit entries).
 */
final class SeedWriter {

    private static final int ROWS_PER_STATEMENT = 500;

    private final JdbcTemplate jdbc;

    SeedWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    int write(List<SeedEmployee> employees) {
        insertEmployees(employees);
        return insertRecords(employees);
    }

    private void insertEmployees(List<SeedEmployee> employees) {
        String columns = "(id, employee_id, first_name, last_name, work_email, job_title, department, country, city, "
                + "employment_type, hire_date, status)";
        for (int from = 0; from < employees.size(); from += ROWS_PER_STATEMENT) {
            List<SeedEmployee> chunk = employees.subList(from, Math.min(from + ROWS_PER_STATEMENT, employees.size()));
            List<Object> args = new ArrayList<>(chunk.size() * 12);
            for (SeedEmployee e : chunk) {
                args.add(e.id().toString());
                args.add(e.employeeId());
                args.add(e.firstName());
                args.add(e.lastName());
                args.add(e.workEmail());
                args.add(e.jobTitle());
                args.add(e.department());
                args.add(e.country().name());
                args.add(e.city());
                args.add(e.employmentType().name());
                args.add(e.hireDate());
                args.add(e.status().name());
            }
            jdbc.update("INSERT INTO employee " + columns + " VALUES " + placeholders(chunk.size(), 12), args.toArray());
        }
    }

    private int insertRecords(List<SeedEmployee> employees) {
        List<Object[]> rows = new ArrayList<>();
        for (SeedEmployee e : employees) {
            for (SeedCompensation c : e.compensation()) {
                rows.add(new Object[] {
                    c.id().toString(), e.id().toString(), c.amount(), c.currency().getCurrencyCode(),
                    c.effectiveStart(), c.effectiveEnd()
                });
            }
        }
        String columns = "(id, employee_id, amount, currency, effective_start, effective_end)";
        for (int from = 0; from < rows.size(); from += ROWS_PER_STATEMENT) {
            List<Object[]> chunk = rows.subList(from, Math.min(from + ROWS_PER_STATEMENT, rows.size()));
            List<Object> args = new ArrayList<>(chunk.size() * 6);
            for (Object[] row : chunk) {
                for (Object value : row) {
                    args.add(value);
                }
            }
            jdbc.update("INSERT INTO compensation_record " + columns + " VALUES " + placeholders(chunk.size(), 6), args.toArray());
        }
        return rows.size();
    }

    private static String placeholders(int rows, int columns) {
        String row = "(" + String.join(", ", java.util.Collections.nCopies(columns, "?")) + ")";
        return String.join(", ", java.util.Collections.nCopies(rows, row));
    }
}
