package com.acme.salary.auth.application;

import com.acme.salary.auth.api.UserIdentity;

public record LoginResult(IssuedToken token, UserIdentity user) {}
