package com.claseya.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Fails fast at startup when a deployment outside local development would run without the
 * abuse-protection controls or public URLs the documentation promises (CFG-01).
 *
 * <p>Malware scanning and the registration CAPTCHA are opt-in ({@code false} by default), so a
 * forgotten environment variable used to produce a healthy-looking deployment that stored
 * unscanned evidence and accepted registrations with no human challenge. The demo seed is the
 * opposite risk: enabling it outside development injects fake accounts and teachers.
 *
 * <p>OPS-004 adds the deployment-critical settings that used to fail only at runtime: the public
 * SPA origin (activation/canonical links), the CORS origin, secure cookies and the email transport.
 *
 * <p>Like {@link JwtSecretValidator}, this is a configuration check, not a behaviour change: with
 * the expected values the application starts exactly as before.
 */
@Component
public class ProductionConfigValidator {

    /** Profiles where the insecure defaults are intentional (tests, local development). */
    static final List<String> NON_PRODUCTION_PROFILES = List.of("local", "test");

    private static final String SCAN_ENABLED = "app.storage.scan.enabled";
    private static final String ALLOW_UNSCANNED = "app.storage.scan.allow-unscanned";
    private static final String CAPTCHA_ENABLED = "app.auth.captcha.enabled";
    private static final String DEMO_SEED_ENABLED = "app.demo-seed.enabled";
    private static final String PUBLIC_URL = "app.public-url";
    private static final String ALLOWED_ORIGINS = "cors.allowed-origins";
    private static final String COOKIE_SECURE = "auth.cookie.secure";
    private static final String COOKIE_SAME_SITE = "auth.cookie.same-site";
    private static final String EMAIL_TRANSPORT = "app.notification.email.transport";
    private static final String MAIL_HOST = "spring.mail.host";
    private static final String EMAIL_API_KEY = "app.notification.email.api-key";

    public ProductionConfigValidator(Environment environment) {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        validate(
                activeProfiles,
                environment.getProperty(SCAN_ENABLED, Boolean.class, false),
                environment.getProperty(ALLOW_UNSCANNED, Boolean.class, false),
                environment.getProperty(CAPTCHA_ENABLED, Boolean.class, false),
                environment.getProperty(DEMO_SEED_ENABLED, Boolean.class, false));
        validateDeployment(
                activeProfiles,
                environment.getProperty(PUBLIC_URL, "http://localhost:5173"),
                environment.getProperty(ALLOWED_ORIGINS, ""),
                environment.getProperty(COOKIE_SECURE, Boolean.class, true),
                environment.getProperty(COOKIE_SAME_SITE, "Lax"),
                environment.getProperty(EMAIL_TRANSPORT, "smtp"),
                environment.getProperty(MAIL_HOST, ""),
                environment.getProperty(EMAIL_API_KEY, ""));
    }

    /**
     * @throws IllegalStateException when a production-like profile has scanning off without the
     *                               explicit acknowledgement, CAPTCHA off, or the demo seed on.
     */
    static void validate(Collection<String> activeProfiles,
                         boolean scanEnabled,
                         boolean allowUnscannedEvidence,
                         boolean captchaEnabled,
                         boolean demoSeedEnabled) {
        if (isNonProduction(activeProfiles)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        if (!scanEnabled && !allowUnscannedEvidence) {
            problems.add("app.storage.scan.enabled (SCAN_ENABLED) must be true unless "
                    + "ALLOW_UNSCANNED_EVIDENCE=true acknowledges unscanned evidence");
        }
        if (!captchaEnabled) {
            problems.add("app.auth.captcha.enabled (CAPTCHA_ENABLED) must be true");
        }
        if (demoSeedEnabled) {
            problems.add("app.demo-seed.enabled (DEMO_SEED) must be false");
        }
        failIfAny(problems);
    }

    /**
     * Validates the deployment-facing settings that otherwise fail only when a real user tries to
     * register, activate or log in.
     */
    static void validateDeployment(Collection<String> activeProfiles,
                                   String publicUrl,
                                   String allowedOrigins,
                                   boolean cookieSecure,
                                   String sameSite,
                                   String emailTransport,
                                   String mailHost,
                                   String emailApiKey) {
        if (isNonProduction(activeProfiles)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        validateHttpsOrigin(problems, "app.public-url (APP_BASE_URL)", publicUrl);
        validateAllowedOrigins(problems, allowedOrigins);
        if (!cookieSecure) {
            problems.add("auth.cookie.secure (COOKIE_SECURE) must be true outside local/test");
        }
        validateSameSite(problems, sameSite, cookieSecure);
        validateEmailTransport(problems, emailTransport, mailHost, emailApiKey);
        failIfAny(problems);
    }

    private static void validateHttpsOrigin(List<String> problems, String name, String value) {
        if (value == null || value.isBlank()) {
            problems.add(name + " must be set to the public HTTPS origin");
            return;
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || host.isBlank()
                    || isLocalHost(host)) {
                problems.add(name + " must be a public HTTPS origin, not '" + value.trim() + "'");
            }
        } catch (IllegalArgumentException ex) {
            problems.add(name + " is not a valid URL: '" + value.trim() + "'");
        }
    }

    private static void validateAllowedOrigins(List<String> problems, String allowedOrigins) {
        List<String> origins = Arrays.stream((allowedOrigins == null ? "" : allowedOrigins).split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (origins.isEmpty()) {
            problems.add("cors.allowed-origins (FRONTEND_URL) must contain the public SPA origin");
            return;
        }
        for (String origin : origins) {
            validateHttpsOrigin(problems, "cors.allowed-origins (FRONTEND_URL)", origin);
        }
    }

    private static void validateSameSite(List<String> problems, String sameSite, boolean cookieSecure) {
        String normalized = sameSite == null ? "" : sameSite.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("lax") && !normalized.equals("strict") && !normalized.equals("none")) {
            problems.add("auth.cookie.same-site (COOKIE_SAME_SITE) must be Lax, Strict or None");
            return;
        }
        if (normalized.equals("none") && !cookieSecure) {
            problems.add("COOKIE_SAME_SITE=None requires COOKIE_SECURE=true");
        }
    }

    private static void validateEmailTransport(List<String> problems,
                                               String emailTransport,
                                               String mailHost,
                                               String emailApiKey) {
        String transport = emailTransport == null || emailTransport.isBlank()
                ? "smtp"
                : emailTransport.trim().toLowerCase(Locale.ROOT);
        if (transport.equals("smtp")) {
            if (mailHost == null || mailHost.isBlank()) {
                problems.add("spring.mail.host (MAIL_HOST) is required when EMAIL_TRANSPORT=smtp; "
                        + "use EMAIL_TRANSPORT=brevo when the host blocks outbound SMTP");
            }
        } else if (transport.equals("brevo")) {
            if (emailApiKey == null || emailApiKey.isBlank()) {
                problems.add("EMAIL_API_KEY is required when EMAIL_TRANSPORT=brevo");
            }
        } else {
            problems.add("EMAIL_TRANSPORT must be 'smtp' or 'brevo'");
        }
    }

    private static boolean isLocalHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("0.0.0.0")
                || normalized.equals("[::1]")
                || normalized.equals("::1")
                || normalized.endsWith(".localhost");
    }

    private static boolean isNonProduction(Collection<String> activeProfiles) {
        return activeProfiles.stream().anyMatch(NON_PRODUCTION_PROFILES::contains);
    }

    private static void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe configuration outside local/test: "
                    + String.join("; ", problems)
                    + ". Set the environment variables, or activate the local/test profile.");
        }
    }
}
