package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.acme.salary.auth.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** How the demo password is found: the environment variable first, then the app.seed property. */
class DemoAccountSeederConfigTest {

    @Configuration
    @Import(DemoAccountSeeder.class)
    static class Config {

        @Bean
        AppUserRepository users() {
            return mock(AppUserRepository.class);
        }

        @Bean
        PasswordEncoder encoder() {
            return new BCryptPasswordEncoder(4);
        }
    }

    // the seeder only exists under the local or seed profile
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("seed"))
            .withUserConfiguration(Config.class);

    @Test
    void usesThePropertyThatTheLocalProfileProvides() {
        runner.withPropertyValues("app.seed.hr-password=Local-Default-1")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DemoAccountSeeder.class));
    }

    @Test
    void usesTheEnvironmentVariableWhenNoPropertyIsSet() {
        runner.withPropertyValues("DEMO_HR_PASSWORD=From-The-Environment-1")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(DemoAccountSeeder.class));
    }

    @Test
    void refusesToStartWithAClearMessageWhenNeitherIsSet() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("DEMO_HR_PASSWORD must be set");
        });
    }
}
