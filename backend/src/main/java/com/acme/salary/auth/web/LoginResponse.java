package com.acme.salary.auth.web;

import com.acme.salary.auth.api.UserIdentity;

record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    record UserResponse(String id, String email, String displayName) {

        static UserResponse from(UserIdentity user) {
            return new UserResponse(user.id().toString(), user.email(), user.displayName());
        }
    }
}
