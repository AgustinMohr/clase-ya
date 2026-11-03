package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorTest {

    @Test
    void localProfileSkipsEveryCheck() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of("local"), false, false, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfileSkipsEveryCheck() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of("test"), false, false, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsDisabledScanningWithoutAcknowledgement() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), false, false, true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCAN_ENABLED")
                .hasMessageContaining("ALLOW_UNSCANNED_EVIDENCE");
    }

    @Test
    void productionAcceptsUnscannedEvidenceWithExplicitAcknowledgement() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of(), false, true, true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void theAcknowledgementDoesNotBypassTheCaptcha() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), false, true, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAPTCHA_ENABLED");
    }

    @Test
    void productionRejectsDisabledCaptcha() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), true, false, false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAPTCHA_ENABLED");
    }

    @Test
    void productionRejectsDemoSeed() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), true, false, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED");
    }

    @Test
    void productionAcceptsSafeConfiguration() {
        assertThatCode(() -> ProductionConfigValidator.validate(List.of(), true, false, true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void everyMissingControlIsReportedTogether() {
        assertThatThrownBy(() -> ProductionConfigValidator.validate(List.of(), false, false, false, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCAN_ENABLED")
                .hasMessageContaining("CAPTCHA_ENABLED")
                .hasMessageContaining("DEMO_SEED");
    }
}
