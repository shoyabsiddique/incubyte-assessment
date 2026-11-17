package com.acme.salary.employees.domain;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.FieldError;
import com.acme.salary.shared.domain.ValidationFailedException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A valid employee profile: every field parsed, trimmed and within its limits. */
public record EmployeeDraft(
        String firstName,
        String lastName,
        String workEmail,
        String jobTitle,
        String department,
        Country country,
        String city,
        EmploymentType employmentType,
        LocalDate hireDate,
        EmploymentStatus status) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final LocalDate EARLIEST_HIRE = LocalDate.of(1950, 1, 1);

    /** Parses the input or fails with one error per invalid field. */
    public static EmployeeDraft parse(ProfileInput input) {
        List<FieldError> errors = new ArrayList<>();
        EmployeeDraft draft = parse(input, errors);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return draft;
    }

    /** Adds this input's problems to {@code errors}; the result is only meaningful when none were added. */
    static EmployeeDraft parse(ProfileInput input, List<FieldError> errors) {
        String firstName = text("firstName", input.firstName(), 100, errors);
        String lastName = text("lastName", input.lastName(), 100, errors);
        String email = email(input.workEmail(), errors);
        String jobTitle = text("jobTitle", input.jobTitle(), 100, errors);
        String department = text("department", input.department(), 100, errors);
        Country country = choice("country", input.country(), Country.class, errors);
        String city = text("city", input.city(), 100, errors);
        EmploymentType type = choice("employmentType", input.employmentType(), EmploymentType.class, errors);
        LocalDate hired = date("hireDate", input.hireDate(), errors);
        if (hired != null && hired.isBefore(EARLIEST_HIRE)) {
            errors.add(new FieldError("hireDate", "must not be before " + EARLIEST_HIRE));
        }
        EmploymentStatus status = choice("status", input.status(), EmploymentStatus.class, errors);
        return new EmployeeDraft(firstName, lastName, email, jobTitle, department, country, city, type, hired, status);
    }

    static String collapse(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    static LocalDate date(String field, String value, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldError(field, "is required"));
            return null;
        }
        try {
            LocalDate parsed = LocalDate.parse(value.trim());
            if (parsed.getYear() < 1900 || parsed.getYear() > 2100) {
                errors.add(new FieldError(field, "must be a date between 1900 and 2100"));
                return null;
            }
            return parsed;
        } catch (DateTimeParseException invalid) {
            errors.add(new FieldError(field, "must be a real date written as yyyy-mm-dd"));
            return null;
        }
    }

    private static String text(String field, String value, int max, List<FieldError> errors) {
        String collapsed = collapse(value);
        if (collapsed.isEmpty()) {
            errors.add(new FieldError(field, "is required"));
        } else if (collapsed.length() > max) {
            errors.add(new FieldError(field, "must be at most " + max + " characters"));
        }
        return collapsed;
    }

    private static String email(String value, List<FieldError> errors) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            errors.add(new FieldError("workEmail", "is required"));
        } else if (trimmed.length() > 254 || !EMAIL.matcher(trimmed).matches()) {
            errors.add(new FieldError("workEmail", "must be a valid email address"));
        }
        return trimmed;
    }

    private static <E extends Enum<E>> E choice(String field, String value, Class<E> type, List<FieldError> errors) {
        if (value == null || value.isBlank()) {
            errors.add(new FieldError(field, "is required"));
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException unknown) {
            errors.add(new FieldError(field, "is not a known value"));
            return null;
        }
    }
}
