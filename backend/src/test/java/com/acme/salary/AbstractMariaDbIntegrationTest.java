package com.acme.salary;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base for integration tests: boots the whole application against a real MariaDB 11.4 in
 * Testcontainers (the same engine family as production), with Flyway applying the migrations and
 * Hibernate validating the schema. Needs Docker; run with {@code ./gradlew integrationTest}.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractMariaDbIntegrationTest {}
