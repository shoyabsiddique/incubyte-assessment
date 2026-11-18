package com.acme.salary.shared.domain;

import java.util.List;

/** Input that failed validation, with every problem found rather than only the first. */
public class ValidationFailedException extends DomainException {

    private final transient List<FieldError> errors;

    public ValidationFailedException(List<FieldError> errors) {
        this("VALIDATION_FAILED", errors);
    }

    /** A validation failure with its own stable code, for a rule the client should recognise. */
    public ValidationFailedException(String code, List<FieldError> errors) {
        super(ErrorKind.INVALID_INPUT, code, "The request has invalid fields");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
