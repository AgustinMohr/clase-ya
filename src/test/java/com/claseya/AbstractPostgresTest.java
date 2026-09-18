package com.claseya;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Starts a single PostgreSQL test container per JVM (not per test class). JUnit's
 * per-class lifecycle restarts the shared container between classes and hangs on
 * this Docker setup, so the container is started once here and stopped on JVM exit.
 */
public abstract class AbstractPostgresTest {

    protected static final String TEST_SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya")
                .withUsername("claseya")
                .withPassword("claseya");
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> TEST_SECRET);
        registry.add("jwt.expiration", () -> "3600");
    }
}
