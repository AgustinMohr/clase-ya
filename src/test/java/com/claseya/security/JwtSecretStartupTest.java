package com.claseya.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the real startup behaviour: the application context refuses to boot with an unsafe JWT
 * secret outside local development, and still boots with a valid one. No database needed.
 */
class JwtSecretStartupTest {

    private static final String VALID = "a-strong-and-unique-secret-with-more-than-32-bytes";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(JwtSecretValidator.class);

    @Test
    void validSecret_starts() {
        runner.withPropertyValues("jwt.secret=" + VALID)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void defaultSecret_failsOutsideLocal() {
        runner.withPropertyValues("jwt.secret=" + JwtSecretValidator.DEV_DEFAULT_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void shortSecret_fails() {
        runner.withPropertyValues("jwt.secret=too-short")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void localProfile_keepsTheDefaultWorking() {
        runner.withPropertyValues("jwt.secret=" + JwtSecretValidator.DEV_DEFAULT_SECRET,
                        "spring.profiles.active=local")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
