package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.auth.domain.InvalidCredentialsException;
import com.acme.salary.auth.domain.JwtSecret;
import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final JwtSecret SECRET = JwtSecret.of("unit-test-secret-0123456789-abcdefghij");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final PasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    private AuthService service;
    private AppUserEntity priya;

    @BeforeEach
    void setUp() {
        TokenService tokens = new TokenService(JwtConfig.encoder(SECRET), Clock.systemUTC(), Duration.ofHours(1));
        service = new AuthService(users, encoder, tokens);
        priya = new AppUserEntity(UUID.randomUUID(), "priya@acme.example", encoder.encode("Right-Pass1"), "Priya");
        when(users.findByEmail("priya@acme.example")).thenReturn(Optional.of(priya));
        when(users.findByEmail("nobody@acme.example")).thenReturn(Optional.empty());
    }

    @Test
    void signsInWithTheRightPasswordAndReturnsTheUserAndAToken() {
        LoginResult result = service.login("priya@acme.example", "Right-Pass1");

        assertThat(result.user().id()).isEqualTo(priya.getId());
        assertThat(result.user().email()).isEqualTo("priya@acme.example");
        assertThat(result.token().value()).isNotBlank();
        assertThat(result.token().expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void ignoresCaseAndSurroundingSpacesInTheEmail() {
        assertThat(service.login("  Priya@ACME.example ", "Right-Pass1").user().email())
                .isEqualTo("priya@acme.example");
    }

    @Test
    void aWrongPasswordIsRejectedGenerically() {
        assertThatThrownBy(() -> service.login("priya@acme.example", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void anUnknownEmailGivesTheSameFailureAndStillSpendsTheHashingTime() {
        assertThatThrownBy(() -> service.login("nobody@acme.example", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        // a comparison against a dummy hash keeps unknown emails indistinguishable by timing
        verify(encoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    void blankOrMissingFieldsAreRejectedTheSameWay() {
        for (String[] attempt : new String[][] {{null, "x"}, {"", "x"}, {"  ", "x"}, {"priya@acme.example", null}, {"priya@acme.example", ""}}) {
            assertThatThrownBy(() -> service.login(attempt[0], attempt[1]))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }
    }

    @Test
    void aPasswordLongerThanBcryptCanUseIsRejectedInsteadOfCrashing() {
        assertThatThrownBy(() -> service.login("priya@acme.example", "a".repeat(100)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void anAbsurdlyLongEmailIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.login("a".repeat(300) + "@acme.example", "Right-Pass1"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(users, never()).findByEmail(org.mockito.ArgumentMatchers.argThat(email -> email.length() > 254));
    }
}
