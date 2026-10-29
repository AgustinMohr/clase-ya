package com.claseya.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Fails fast at startup when a deployment outside local development would run without the
 * abuse-protection controls the documentation promises (CFG-01).
 *
 * <p>Malware scanning and the registration CAPTCHA are opt-in ({@code false} by default), so a
 * forgotten environment variable used to produce a healthy-looking deployment that stored
 * unscanned evidence and accepted registrations with no human challenge. The demo seed is the
 * opposite risk: enabling it outside development injects fake accounts and teachers.
 *
 * <p>Like {@link JwtSecretValidator}, this is a configuration check, not a behaviour change: with
 * the expected values the application starts exactly as before.
 */
@Component
public class ProductionConfigValidator {

    /** Profiles where the insecure defaults are intentional (tests, local development). */
    static final List<String> NON_PRODUCTION_PROFILES = List.of("local", "test");

    private static final String SCAN_ENABLED = "app.storage.scan.enabled";
    private static final String CAPTCHA_ENABLED = "app.auth.captcha.enabled";
    private static final String DEMO_SEED_ENABLED = "app.demo-seed.enabled";

    public ProductionConfigValidator(Environment environment) {
        validate(
                Arrays.asList(environment.getActiveProfiles()),
                environment.getProperty(SCAN_ENABLED, Boolean.class, false),
                environment.getProperty(CAPTCHA_ENABLED, Boolean.class, false),
                environment.getProperty(DEMO_SEED_ENABLED, Boolean.class, false));
    }

    /**
     * @throws IllegalStateException when a production-like profile has scanning or CAPTCHA off,
     *                               or the demo seed on.
     */
    static void validate(Collection<String> activeProfiles,
                         boolean scanEnabled,
                         boolean captchaEnabled,
                         boolean demoSeedEnabled) {
        if (activeProfiles.stream().anyMatch(NON_PRODUCTION_PROFILES::contains)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        if (!scanEnabled) {
            problems.add("app.storage.scan.enabled (SCAN_ENABLED) must be true");
        }
        if (!captchaEnabled) {
            problems.add("app.auth.captcha.enabled (CAPTCHA_ENABLED) must be true");
        }
        if (demoSeedEnabled) {
            problems.add("app.demo-seed.enabled (DEMO_SEED) must be false");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe configuration outside local/test: "
                    + String.join("; ", problems)
                    + ". Set the environment variables, or activate the local/test profile.");
        }
    }
}
