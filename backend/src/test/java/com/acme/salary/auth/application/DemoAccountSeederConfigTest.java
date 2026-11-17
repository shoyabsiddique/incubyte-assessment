package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.salary.auth.persistence.AppUserEntity;
import com.acme.salary.auth.persistence.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Which password the demo accounts get: the DEMO_HR_PASSWORD environment variable when it has a
 * value, otherwise the app.seed.hr-password property (the local profile's development default).
 */
class DemoAccountSeederConfigTest {

    private static final AppUserRepository USERS = mock(AppUserRepository.class);
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Configuration
    @Import(DemoAccountSeeder.class)
    static class Config {

        @Bean
        AppUserRepository users() {
            return USERS;
        }

        @Bean
        PasswordEncoder encoder() {
            return ENCODER;
        }
    }

    // The seeder only exists under the local or seed profile. "DEMO_HR_PASSWORD=" is set in every
    // test so a real variable on the machine running the tests can never leak into the result.
    private ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("seed"))
                .withUserConfiguration(Config.class)
                .withPropertyValues("DEMO_HR_PASSWORD=")
                .withPropertyValues(properties);
    }

    private static String passwordGivenToTheDemoAccounts(DemoAccountSeeder seeder) {
        org.mockito.Mockito.clearInvocations(USERS);
        seeder.run(null);
        ArgumentCaptor<AppUserEntity> saved = ArgumentCaptor.forClass(AppUserEntity.class);
        verify(USERS, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        for (String candidate : new String[] {"Local-Default-1", "From-The-Environment-1"}) {
            if (ENCODER.matches(candidate, hash)) {
                return candidate;
            }
        }
        return "unknown";
    }

    @Test
    void usesThePropertyThatTheLocalProfileProvides() {
        runner("app.seed.hr-password=Local-Default-1").run(context ->
                assertThat(passwordGivenToTheDemoAccounts(context.getBean(DemoAccountSeeder.class))).isEqualTo("Local-Default-1"));
    }

    @Test
    void usesTheEnvironmentVariableWhenNoPropertyIsSet() {
        runner("DEMO_HR_PASSWORD=From-The-Environment-1").run(context ->
                assertThat(passwordGivenToTheDemoAccounts(context.getBean(DemoAccountSeeder.class))).isEqualTo("From-The-Environment-1"));
    }

    @Test
    void theEnvironmentVariableWinsWhenBothAreSet() {
        runner("DEMO_HR_PASSWORD=From-The-Environment-1", "app.seed.hr-password=Local-Default-1").run(context ->
                assertThat(passwordGivenToTheDemoAccounts(context.getBean(DemoAccountSeeder.class))).isEqualTo("From-The-Environment-1"));
    }

    @Test
    void aBlankEnvironmentVariableFallsBackToTheProperty() {
        runner("DEMO_HR_PASSWORD=   ", "app.seed.hr-password=Local-Default-1").run(context ->
                assertThat(passwordGivenToTheDemoAccounts(context.getBean(DemoAccountSeeder.class))).isEqualTo("Local-Default-1"));
    }

    @Test
    void refusesToStartWithAClearMessageWhenNeitherIsSet() {
        runner().run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("DEMO_HR_PASSWORD must be set");
        });
    }
}
