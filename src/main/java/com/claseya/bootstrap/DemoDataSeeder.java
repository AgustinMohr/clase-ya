package com.claseya.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * DEVELOPMENT-ONLY demo data. Applies {@code db/seed/demo-data.sql} on startup so
 * local runs show a fully populated UI with no manual step.
 *
 * <p><strong>Opt-in.</strong> It only runs when {@code app.demo-seed.enabled=true} is
 * set explicitly ({@code DEMO_SEED=true}): a missing property means no seeding, so a
 * production deployment that forgets the variable stays clean. Local development
 * enables it through the {@code local} profile (see {@code application-local.yml}).
 * The script is idempotent and non-destructive, so re-applying it on every boot
 * neither duplicates nor overwrites existing rows.
 *
 * <p>The same script can be applied without booting the app via
 * {@code scripts/seed-demo.ps1}.
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** Classpath location of the seed script (packaged, so it also works in Docker). */
    static final String SCRIPT_LOCATION = "db/seed/demo-data.sql";

    private final DataSource dataSource;

    public DemoDataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.currentTimeMillis();
        apply();
        log.info("Demo seed applied from {} in {} ms (development data; set app.demo-seed.enabled=false to skip)",
                SCRIPT_LOCATION, System.currentTimeMillis() - startedAt);
    }

    /**
     * Runs the seed script on a single connection and transaction. Package-visible
     * (public for the regression test) so the idempotency test can apply it twice.
     */
    public void apply() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        // The script has accented Spanish text: read it as UTF-8 explicitly.
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.addScript(new ClassPathResource(SCRIPT_LOCATION));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
