package com.acme.salary.auth.web;

import com.acme.salary.auth.application.AuthService;
import com.acme.salary.auth.application.LoginResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.email(), request.password());
        return new LoginResponse(
                result.token().value(),
                "Bearer",
                result.token().expiresInSeconds(),
                LoginResponse.UserResponse.from(result.user()));
    }

    /** Who the presented token belongs to. The identity comes from the token, never from the request. */
    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt token) {
        return new MeResponse(token.getSubject(), token.getClaimAsString("email"));
    }

    record MeResponse(String id, String email) {}
}
