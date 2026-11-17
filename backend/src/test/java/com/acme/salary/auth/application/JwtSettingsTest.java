package com.acme.salary.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** The application must refuse to start with a missing or weak signing secret. */
class JwtSettingsTest {

    @Configuration
    @EnableConfigurationProperties(JwtSettings.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void startsWithAStrongSecretAndDefaultsToOneHour() {
        runner.withPropertyValues("app.security.jwt-secret=" + "k".repeat(40)).run(context -> {
            assertThat(context).hasNotFailed();
            JwtSettings settings = context.getBean(JwtSettings.class);
            assertThat(settings.tokenTtl()).isEqualTo(Duration.ofHours(1));
            assertThat(settings.bcryptStrength()).isEqualTo(10);
        });
    }

    @Test
    void refusesToStartWithAWeakSecret() {
        runner.withPropertyValues("app.security.jwt-secret=too-short").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("at least 32 bytes");
        });
    }

    @Test
    void refusesToStartWithoutASecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void doesNotPutTheSecretInTheStartupFailureMessage() {
        runner.withPropertyValues("app.security.jwt-secret=weak-but-private").run(context ->
                assertThat(context.getStartupFailure()).hasStackTraceContaining("at least 32 bytes")
                        .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("weak-but-private")));
    }
}
