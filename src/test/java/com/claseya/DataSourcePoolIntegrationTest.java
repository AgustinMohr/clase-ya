package com.claseya;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RT-09: the connection pool is configured explicitly for a serverless database. A change that
 * drops these settings would silently go back to HikariCP defaults (no JDBC connect timeout,
 * 30 s connection wait, 10 idle connections kept open).
 */
@SpringBootTest
class DataSourcePoolIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void poolUsesTheBoundedNeonOrientedSettings() throws Exception {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);

        assertThat(pool.getPoolName()).isEqualTo("claseya");
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getMinimumIdle()).isEqualTo(2);
        assertThat(pool.getIdleTimeout()).isEqualTo(120_000);
        assertThat(pool.getMaxLifetime()).isEqualTo(600_000);
        assertThat(pool.getConnectionTimeout()).isEqualTo(10_000);
        assertThat(pool.getValidationTimeout()).isEqualTo(5_000);
        assertThat(pool.getDataSourceProperties().getProperty("connectTimeout")).isEqualTo("10");
        assertThat(pool.getDataSourceProperties().getProperty("tcpKeepAlive")).isEqualTo("true");
    }
}
