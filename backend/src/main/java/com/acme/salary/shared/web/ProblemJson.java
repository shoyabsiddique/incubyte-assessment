package com.acme.salary.shared.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/**
 * Writes a Problem Details body straight to the response, for failures raised by the security
 * filters before any controller or advice can run. Same shape as {@link ApiExceptionHandler}.
 */
public final class ProblemJson {

    private ProblemJson() {}

    public static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        String body = "{\"type\":\"about:blank\",\"title\":\"" + escape(status.getReasonPhrase())
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + escape(detail)
                + "\",\"code\":\"" + escape(code) + "\"}";
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
