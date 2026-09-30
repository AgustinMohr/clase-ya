package com.claseya.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Guards the fail-safe default of the demo seed: it must be <strong>opt-in</strong>,
 * so a deployment that forgets {@code DEMO_SEED} never writes demo data.
 *
 * <p>Runs the condition only (no database, no container), which is exactly the
 * behaviour under test: whether the seeder is registered at all.
 */
class DemoDataSeederConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withUserConfiguration(DemoDataSeeder.class);

    @Test
    void isNotRegisteredWhenThePropertyIsMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    @Test
    void isNotRegisteredWhenExplicitlyDisabled() {
        runner.withPropertyValues("app.demo-seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoDataSeeder.class));
    }

    @Test
    void isRegisteredWhenExplicitlyEnabled() {
        runner.withPropertyValues("app.demo-seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoDataSeeder.class));
    }
}
