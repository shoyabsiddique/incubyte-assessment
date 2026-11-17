package com.acme.salary.employees.domain;

import java.util.Locale;

/** Turns a search word into a contains-pattern in which the user's own characters are literal. */
public final class SearchTerms {

    /** The LIKE escape character the query must declare; a backslash is awkward in MySQL string literals. */
    public static final char ESCAPE = '!';

    private SearchTerms() {}

    public static String likePattern(String token) {
        String escaped = token.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
