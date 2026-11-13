package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorSecurityTogglesTest {

    @Test
    void localProfileSkipsEveryToggleCheck() {
        assertThatCode(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of("local"), true, true, false, false))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfileSkipsEveryToggleCheck() {
        assertThatCode(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of("test"), true, true, false, false))
                .doesNotThrowAnyException();
    }

    @Test
    void productionAcceptsSafeToggles() {
        assertThatCode(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), false, false, true, true))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsActivationLinkLogging() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), true, false, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_LOG_ACTIVATION_LINKS");
    }

    @Test
    void productionRejectsContactLinkLogging() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), false, true, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MESSAGE_LINK_LOG");
    }

    @Test
    void productionRejectsDisabledCsrf() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), false, false, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("security.csrf.enabled");
    }

    @Test
    void productionRejectsDisabledRateLimit() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), false, false, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RATE_LIMIT_ENABLED");
    }

    @Test
    void everyUnsafeToggleIsReportedTogether() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateSecurityToggles(
                List.of(), true, true, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_LOG_ACTIVATION_LINKS")
                .hasMessageContaining("MESSAGE_LINK_LOG")
                .hasMessageContaining("security.csrf.enabled")
                .hasMessageContaining("RATE_LIMIT_ENABLED");
    }
}
