package com.acme.salary.auth.api;

import java.util.UUID;

/** Who a signed-in user is. The id is the only identity other modules should record. */
public record UserIdentity(UUID id, String email, String displayName) {}
