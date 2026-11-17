package com.acme.salary.audit.domain;

import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * A validated request for a page of audit entries. Dates are whole days in UTC: {@code from} starts
 * its day and the end date is inclusive, so {@code toExclusive} is the start of the following day.
 */
public record AuditQuery(
        int page,
        int size,
        Optional<UUID> userId,
        Optional<AuditEntityType> entityType,
        Optional<Instant> from,
        Optional<Instant> toExclusive,
        Optional<UUID> subjectEmployeeId) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAXIMUM_SIZE = 100;
    public static final int MAXIMUM_PAGE = 1_000_000;

    public static AuditQuery of(String page, String size, String userId, String entityType, String from, String to) {
        int pageNumber = page == null || page.isBlank() ? 0 : whole(page, "page");
        int pageSize = size == null || size.isBlank() ? DEFAULT_SIZE : whole(size, "size");
        if (pageNumber < 0 || pageNumber > MAXIMUM_PAGE) {
            throw invalid("INVALID_PAGE", "page must be between 0 and " + MAXIMUM_PAGE);
        }
        if (pageSize < 1) {
            throw invalid("INVALID_PAGE", "size must be at least 1");
        }
        if (pageSize > MAXIMUM_SIZE) {
            throw invalid("PAGE_SIZE_TOO_LARGE", "size must be at most " + MAXIMUM_SIZE);
        }
        Optional<Instant> start = day(from, "from").map(d -> d.atStartOfDay(ZoneOffset.UTC).toInstant());
        Optional<Instant> end = day(to, "to").map(d -> d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (start.isPresent() && end.isPresent() && !start.get().isBefore(end.get())) {
            throw invalid("INVALID_FILTER", "from must not be after to");
        }
        return new AuditQuery(
                pageNumber, pageSize, uuid(userId), entityType(entityType), start, end, Optional.empty());
    }

    public AuditQuery forEmployee(UUID employeeId) {
        return new AuditQuery(page, size, userId, entityType, from, toExclusive, Optional.of(employeeId));
    }

    private static int whole(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            throw invalid("INVALID_PAGE", name + " must be a whole number");
        }
    }

    private static Optional<UUID> uuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException notAnId) {
            throw invalid("INVALID_FILTER", "userId must be a valid id");
        }
    }

    private static Optional<AuditEntityType> entityType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AuditEntityType.valueOf(value.trim()));
        } catch (IllegalArgumentException unknown) {
            throw invalid("INVALID_FILTER", "entityType must be one of EMPLOYEE, COMPENSATION_RECORD, EXCHANGE_RATE");
        }
    }

    private static Optional<LocalDate> day(String value, String name) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException notADate) {
            throw invalid("INVALID_FILTER", name + " must be a date written as yyyy-mm-dd");
        }
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(ErrorKind.INVALID_INPUT, code, message);
    }
}
