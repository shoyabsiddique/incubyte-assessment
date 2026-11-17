package com.acme.salary.shared.domain;

/** A business failure with a stable, machine-readable code that clients can rely on. */
public class DomainException extends RuntimeException {

    private final ErrorKind kind;
    private final String code;

    public DomainException(ErrorKind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public ErrorKind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
