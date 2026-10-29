package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    @Test
    void localProfileSkipsEveryCheck() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of("local"), false, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfileSkipsEveryCheck() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of("test"), false, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsDisabledScanning() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), false, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCAN_ENABLED");
    }

    @Test
    void productionRejectsDisabledCaptcha() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), true, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAPTCHA_ENABLED");
    }

    @Test
    void productionRejectsDemoSeed() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), true, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED");
    }

    @Test
    void productionAcceptsSafeConfiguration() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of(), true, true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void everyMissingControlIsReportedTogether() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), false, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCAN_ENABLED")
                .hasMessageContaining("CAPTCHA_ENABLED")
                .hasMessageContaining("DEMO_SEED");
    }
}
