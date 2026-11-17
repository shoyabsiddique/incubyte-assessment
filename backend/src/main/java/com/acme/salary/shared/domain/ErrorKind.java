package com.acme.salary.shared.domain;

/** The kinds of failure the API reports, independent of HTTP; the web layer maps each to a status. */
public enum ErrorKind {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT
}
