package com.claseya.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigValidatorDeploymentTest {

    private static final String PUBLIC_URL = "https://claseya.app";
    private static final String ALLOWED_ORIGINS = "https://claseya.app,https://www.claseya.app";

    @Test
    void localAndTestProfilesSkipEveryDeploymentCheck() {
        assertThatCode(() -> ProductionConfigValidator.validateDeployment(
                List.of("local"), "http://localhost:5173", "http://localhost:5173",
                false, "None", "unknown", "", ""))
                .doesNotThrowAnyException();

        assertThatCode(() -> ProductionConfigValidator.validateDeployment(
                List.of("test"), "", "", false, "", "", "", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void productionAcceptsACompleteSmtpDeployment() {
        assertThatCode(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "Lax",
                "smtp", "smtp.example.com", ""))
                .doesNotThrowAnyException();
    }

    @Test
    void productionAcceptsABrevoDeploymentWithoutSmtpHost() {
        assertThatCode(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "None",
                "brevo", "", "brevo-key"))
                .doesNotThrowAnyException();
    }

    @Test
    void productionRejectsLocalhostPublicUrl() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), "http://localhost:5173", ALLOWED_ORIGINS, true, "Lax",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BASE_URL");
    }

    @Test
    void productionRejectsPlainHttpPublicUrl() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), "http://claseya.app", ALLOWED_ORIGINS, true, "Lax",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public HTTPS origin");
    }

    @Test
    void productionRejectsMissingOrLocalhostCorsOrigins() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, "", true, "Lax", "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_URL");

        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, "http://localhost:3000", true, "Lax",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public HTTPS origin");
    }

    @Test
    void productionRejectsInsecureCookies() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, false, "Lax",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SECURE");
    }

    @Test
    void sameSiteNoneRequiresSecureCookies() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, false, "None",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SAME_SITE=None");
    }

    @Test
    void productionRejectsAnUnknownSameSiteValue() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "sometimes",
                "smtp", "smtp.example.com", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COOKIE_SAME_SITE");
    }

    @Test
    void productionRejectsSmtpWithoutHost() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "Lax",
                "smtp", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_HOST");
    }

    @Test
    void productionRejectsBrevoWithoutApiKey() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "Lax",
                "brevo", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_API_KEY");
    }

    @Test
    void productionRejectsAnUnknownEmailTransport() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), PUBLIC_URL, ALLOWED_ORIGINS, true, "Lax",
                "carrier-pigeon", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_TRANSPORT");
    }

    @Test
    void everyDeploymentProblemIsReportedTogether() {
        assertThatThrownBy(() -> ProductionConfigValidator.validateDeployment(
                List.of(), "http://localhost:5173", "http://localhost:3000",
                false, "None", "brevo", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BASE_URL")
                .hasMessageContaining("FRONTEND_URL")
                .hasMessageContaining("COOKIE_SECURE")
                .hasMessageContaining("EMAIL_API_KEY");
    }
}
