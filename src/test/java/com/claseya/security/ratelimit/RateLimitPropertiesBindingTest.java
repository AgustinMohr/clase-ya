package com.claseya.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsDefaultLimits() {
        runner.run(context -> {
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getLoginIp().getLimit()).isEqualTo(30);
            assertThat(properties.getLoginIp().getWindow()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.getLoginAccount().getLimit()).isEqualTo(5);
            assertThat(properties.getLoginAccount().getWindow()).isEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void bindsEnvironmentOverrides() {
        runner.withPropertyValues(
                        "security.rate-limit.enabled=false",
                        "security.rate-limit.login-ip.limit=3",
                        "security.rate-limit.login-ip.window=PT2M")
                .run(context -> {
                    RateLimitProperties properties = context.getBean(RateLimitProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getLoginIp().getLimit()).isEqualTo(3);
                    assertThat(properties.getLoginIp().getWindow()).isEqualTo(Duration.ofMinutes(2));
                });
    }

    @Test
    void rejectsAnInvalidLimitAtStartup() {
        runner.withPropertyValues("security.rate-limit.login-ip.limit=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsAZeroWindowAtStartup() {
        runner.withPropertyValues("security.rate-limit.login-ip.window=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class TestConfig {
    }
}
