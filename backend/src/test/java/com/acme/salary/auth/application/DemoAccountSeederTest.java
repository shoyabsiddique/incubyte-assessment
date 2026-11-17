package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.acme.salary.auth.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoAccountSeederTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void refusesABlankPassword() {
        assertThatThrownBy(() -> new DemoAccountSeeder(users, encoder, "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_HR_PASSWORD");
    }

    @Test
    void refusesAPasswordTheLoginCouldNeverAccept() {
        assertThatThrownBy(() -> new DemoAccountSeeder(users, encoder, "p".repeat(73)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 bytes");
    }

    @Test
    void refusesTheExamplePlaceholderPassword() {
        assertThatThrownBy(() -> new DemoAccountSeeder(users, encoder, "change-me"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }
}
