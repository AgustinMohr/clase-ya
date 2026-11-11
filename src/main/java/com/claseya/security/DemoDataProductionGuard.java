package com.claseya.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * OPS-003: refuses to leave known demo credentials usable in a public deployment.
 *
 * <p>It runs after the context (and Flyway/JPA) is ready. Outside {@code local}/{@code test}, any
 * account under the demo domain is suspended and its password hash is replaced. The operation is
 * idempotent: already suspended accounts are skipped.
 */
@Component
public class DemoDataProductionGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataProductionGuard.class);

    private final Environment environment;
    private final DemoDataQuarantineService quarantineService;

    public DemoDataProductionGuard(Environment environment,
                                   DemoDataQuarantineService quarantineService) {
        this.environment = environment;
        this.quarantineService = quarantineService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction(environment.getActiveProfiles())) {
            return;
        }
        int quarantined = quarantineService.quarantineDemoAccounts();
        if (quarantined > 0) {
            log.warn("SECURITY: quarantined {} demo account(s) under @claseya.dev. "
                    + "Delete the demo rows before serving real users (OPS-003).", quarantined);
        }
    }

    /** Package-visible for the unit test. */
    static boolean isProduction(String[] activeProfiles) {
        return Arrays.stream(activeProfiles)
                .noneMatch(ProductionConfigValidator.NON_PRODUCTION_PROFILES::contains);
    }
}
