package com.acme.salary.shared.web;

import com.acme.salary.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place errors become RFC 7807 Problem Details, each with a stable code. Spring's own
 * request errors (wrong method, wrong content type, unreadable body) are covered through the base
 * class, and anything unexpected becomes a generic 500 that never leaks internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainError(DomainException error) {
        HttpStatus status =
                switch (error.kind()) {
                    case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
                    case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                    case FORBIDDEN -> HttpStatus.FORBIDDEN;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case CONFLICT -> HttpStatus.CONFLICT;
                };
        return problem(status, error.code(), error.getMessage());
    }

    public ProblemDetail handleUnreadableBody() {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body could not be read");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception error) {
        log.error("Unexpected error while handling a request", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(handleUnreadableBody());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception error, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(error, body, headers, status, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            HttpStatus known = HttpStatus.resolve(status.value());
            problem.setProperty("code", known == null ? "ERROR" : known.name());
        }
        return response;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
