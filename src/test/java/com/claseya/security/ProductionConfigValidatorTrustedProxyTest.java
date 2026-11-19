package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTrustedProxyTest {

    @Test
    void localProfileSkipsTheProxyCheck() {
        assertThatCode(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of("local"), true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfileSkipsTheProxyCheck() {
        assertThatCode(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of("test"), false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void directDeploymentWithoutTrustedHeadersIsAccepted() {
        assertThatCode(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of(), false, false))
                .doesNotThrowAnyException();
    }

    @Test
    void trustedProxyWithForwardedHeadersIsAccepted() {
        assertThatCode(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of(), true, true))
                .doesNotThrowAnyException();
    }

    @Test
    void trustedProxyWithoutForwardedHeadersFails() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of(), true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_TRUST_FORWARDED_HEADERS")
                .hasMessageContaining("BEHIND_TRUSTED_PROXY");
    }

    @Test
    void forwardedHeadersWithoutATrustedProxyFail() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateTrustedProxy(
                List.of(), false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_TRUST_FORWARDED_HEADERS")
                .hasMessageContaining("BEHIND_TRUSTED_PROXY");
    }
}
