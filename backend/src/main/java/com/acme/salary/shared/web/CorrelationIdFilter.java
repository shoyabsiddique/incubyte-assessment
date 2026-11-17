package com.acme.salary.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation ID: reuses a safe incoming {@code X-Correlation-Id} or
 * generates a UUID, returns it on the response and puts it in the logging context so every log
 * line written while handling the request carries it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    // Letters, digits, dash and underscore only: anything else could forge or break log lines.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error e) {
            // The container has not set an error status yet, so report the failure as a 500.
            failed = true;
            throw e;
        } finally {
            try {
                log.info(
                        "{} {} completed with status {}",
                        request.getMethod(),
                        request.getRequestURI(),
                        failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus());
            } finally {
                // Always clear the context so the ID cannot leak to the next request on this thread.
                MDC.remove(MDC_KEY);
            }
        }
    }

    private static String resolve(String supplied) {
        if (supplied != null && SAFE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
