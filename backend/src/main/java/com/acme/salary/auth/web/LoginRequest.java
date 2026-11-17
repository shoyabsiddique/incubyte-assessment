package com.acme.salary.auth.web;

/** Fields may be missing or blank; the service answers every such case with the same generic failure. */
record LoginRequest(String email, String password) {}
