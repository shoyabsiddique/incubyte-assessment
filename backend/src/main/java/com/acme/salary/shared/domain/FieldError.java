package com.acme.salary.shared.domain;

/** One invalid input field and what is wrong with it. */
public record FieldError(String field, String message) {}
