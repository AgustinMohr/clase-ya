package com.claseya;

import com.claseya.auth.repository.EmailVerificationTokenRepository;
import com.claseya.auth.service.AccountActivationService;
import com.claseya.common.exception.BadRequestException;
import com.claseya.common.util.Hashes;
import com.claseya.model.EmailVerificationToken;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-02: activation tokens are consumed with one atomic UPDATE, so concurrent clicks cannot both
 * activate the same link. Runs on its own container because the test commits outside a rollback
 * transaction.
 */
@SpringBootTest(properties = {
        "app.demo-seed.enabled=false",
        "security.rate-limit.enabled=false",
        "security.csrf.enabled=false"
})
@ActiveProfiles("test")
class ActivationConcurrencyIntegrationTest {

    private static final String TEST_SECRET =
            "test-secret-that-is-at-least-thirty-two-bytes-long-for-hmac-sha-256";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("claseya_activation_race")
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
    private AccountActivationService activationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentConsumesAllowExactlyOneWinner() throws Exception {
        EmailVerificationToken token = createToken();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> consume = () -> {
            start.await();
            return new TransactionTemplate(transactionManager)
                    .execute(status -> tokenRepository.consume(token.getId(), Instant.now()));
        };

        List<Integer> results = new ArrayList<>();
        try {
            Future<Integer> first = pool.submit(consume);
            Future<Integer> second = pool.submit(consume);
            start.countDown();
            results.add(first.get(30, TimeUnit.SECONDS));
            results.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(tokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    void concurrentActivationsHaveExactlyOneWinner() throws Exception {
        String rawToken = "race-" + UUID.randomUUID();
        EmailVerificationToken token = createToken(Hashes.sha256Hex(rawToken));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> activate = () -> {
            start.await();
            try {
                activationService.activate(rawToken);
                return true;
            } catch (BadRequestException expected) {
                return false;
            }
        };

        List<Boolean> results = new ArrayList<>();
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(activate));
            }
            start.countDown();
            for (Future<Boolean> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(userRepository.findById(token.getUser().getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(tokenRepository.findById(token.getId()).orElseThrow().getUsedAt()).isNotNull();
    }

    private EmailVerificationToken createToken() {
        return createToken("hash-" + UUID.randomUUID());
    }

    private EmailVerificationToken createToken(String tokenHash) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail("activation-race+" + suffix + "@race.test");
        user.setName("Activation Race " + suffix);
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.PENDING);
        userRepository.saveAndFlush(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return tokenRepository.saveAndFlush(token);
    }
}
