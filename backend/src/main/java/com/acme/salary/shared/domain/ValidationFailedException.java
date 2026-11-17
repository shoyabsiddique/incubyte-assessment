package com.acme.salary.shared.domain;

import java.util.List;

/** Input that failed validation, with every problem found rather than only the first. */
public class ValidationFailedException extends DomainException {

    private final transient List<FieldError> errors;

    public ValidationFailedException(List<FieldError> errors) {
        super(ErrorKind.INVALID_INPUT, "VALIDATION_FAILED", "The request has invalid fields");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
