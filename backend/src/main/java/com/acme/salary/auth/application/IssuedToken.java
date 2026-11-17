package com.acme.salary.auth.application;

public record IssuedToken(String value, long expiresInSeconds) {}
