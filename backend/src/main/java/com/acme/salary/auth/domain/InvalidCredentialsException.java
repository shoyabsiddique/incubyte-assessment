package com.acme.salary.auth.domain;

import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;

/** One answer for every failed sign-in, so it never reveals which part was wrong. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(ErrorKind.UNAUTHENTICATED, "INVALID_CREDENTIALS", "Invalid email or password");
    }
}
