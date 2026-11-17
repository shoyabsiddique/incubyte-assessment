package com.acme.salary.shared.domain;

import java.util.UUID;

/** The signed-in person performing an operation, as recorded in the audit trail. */
public record Actor(UUID userId, String email) {}
