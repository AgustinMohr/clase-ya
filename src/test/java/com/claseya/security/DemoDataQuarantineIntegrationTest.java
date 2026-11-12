package com.claseya.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DemoDataQuarantineIntegrationTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";
    private static final String DEMO_EMAIL = "ops003.demo@claseya.dev";
    private static final String REAL_EMAIL = "ops003.real@example.com";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya_quarantine")
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

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DemoDataQuarantineService service;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void onlyDemoAccountsAreQuarantinedAndTheOperationIsIdempotent() {
        String demoHash = passwordEncoder.encode("Password123");
        String realHash = passwordEncoder.encode("RealPassword123");
        insertUser(DEMO_EMAIL, "Demo", demoHash, "TEACHER");
        insertUser(REAL_EMAIL, "Real", realHash, "STUDENT");

        assertThat(service.quarantineDemoAccounts()).isEqualTo(1);

        Map<String, Object> demo = user(DEMO_EMAIL);
        assertThat(demo.get("status")).isEqualTo("SUSPENDED");
        assertThat(passwordEncoder.matches("Password123", (String) demo.get("password_hash"))).isFalse();

        Map<String, Object> real = user(REAL_EMAIL);
        assertThat(real.get("status")).isEqualTo("ACTIVE");
        assertThat(passwordEncoder.matches("RealPassword123", (String) real.get("password_hash"))).isTrue();

        assertThat(service.quarantineDemoAccounts()).isZero();
    }

    private void insertUser(String email, String name, String passwordHash, String role) {
        jdbc.update("""
                insert into users (email, name, password_hash, role, status)
                values (?, ?, ?, ?, 'ACTIVE')
                """, email, name, passwordHash, role);
    }

    private Map<String, Object> user(String email) {
        return jdbc.queryForMap("select status, password_hash from users where email = ?", email);
    }
}
