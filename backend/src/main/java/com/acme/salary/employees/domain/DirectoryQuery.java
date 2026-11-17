package com.acme.salary.employees.domain;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A validated request for a page of the employee directory. Every value from the outside is parsed
 * here, so nothing unchecked reaches a query.
 */
public record DirectoryQuery(
        int page,
        int size,
        SortField sortField,
        SortDirection direction,
        List<String> searchTokens,
        Optional<Country> country,
        Optional<String> department,
        Optional<String> jobTitle,
        Optional<EmploymentType> employmentType,
        Optional<EmploymentStatus> status) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAXIMUM_SIZE = 100;
    private static final int MAXIMUM_SEARCH_WORDS = 10;
    private static final int MAXIMUM_WORD_LENGTH = 100;

    public static DirectoryQuery of(
            String page,
            String size,
            String sort,
            String q,
            String country,
            String department,
            String jobTitle,
            String employmentType,
            String status) {
        int pageNumber = page == null ? 0 : wholeNumber(page, "INVALID_PAGE", "page");
        int pageSize = size == null ? DEFAULT_SIZE : wholeNumber(size, "INVALID_PAGE", "size");
        if (pageNumber < 0) {
            throw invalid("INVALID_PAGE", "page must not be negative");
        }
        if (pageSize < 1) {
            throw invalid("INVALID_PAGE", "size must be at least 1");
        }
        if (pageSize > MAXIMUM_SIZE) {
            throw invalid("PAGE_SIZE_TOO_LARGE", "size must not be more than " + MAXIMUM_SIZE);
        }

        SortField field = SortField.EMPLOYEE_ID;
        SortDirection direction = SortDirection.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.trim().split(",", -1);
            if (parts.length > 2) {
                throw invalid("INVALID_SORT", "sort must look like field or field,asc or field,desc");
            }
            field = SortField.fromParameter(parts[0].trim())
                    .orElseThrow(() -> invalid("INVALID_SORT", "cannot sort by '" + parts[0].trim() + "'"));
            if (parts.length == 2) {
                direction = switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                    case "asc" -> SortDirection.ASC;
                    case "desc" -> SortDirection.DESC;
                    default -> throw invalid("INVALID_SORT", "the sort direction must be asc or desc");
                };
            }
        }

        return new DirectoryQuery(
                pageNumber,
                pageSize,
                field,
                direction,
                tokens(q),
                optional(country).map(DirectoryQuery::country),
                optional(department).map(DirectoryQuery::collapse),
                optional(jobTitle).map(DirectoryQuery::collapse),
                optional(employmentType).map(value -> enumValue(EmploymentType.class, value, "employmentType")),
                optional(status).map(value -> enumValue(EmploymentStatus.class, value, "status")));
    }

    private static int wholeNumber(String value, String code, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw invalid(code, name + " must be a whole number");
        }
    }

    private static List<String> tokens(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<String> words = Arrays.stream(q.trim().split("\\s+")).toList();
        if (words.size() > MAXIMUM_SEARCH_WORDS) {
            throw invalid("INVALID_SEARCH", "search for at most " + MAXIMUM_SEARCH_WORDS + " words");
        }
        if (words.stream().anyMatch(word -> word.length() > MAXIMUM_WORD_LENGTH)) {
            throw invalid("INVALID_SEARCH", "a search word is too long");
        }
        return words;
    }

    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ");
    }

    private static Country country(String value) {
        return Country.fromDisplayName(value)
                .or(() -> Arrays.stream(Country.values())
                        .filter(c -> c.name().equalsIgnoreCase(value))
                        .findFirst())
                .orElseThrow(() -> invalid("INVALID_FILTER", "unknown country '" + value + "'"));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String name) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> invalid("INVALID_FILTER", "unknown " + name + " '" + value + "'"));
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(ErrorKind.INVALID_INPUT, code, message);
    }
}
