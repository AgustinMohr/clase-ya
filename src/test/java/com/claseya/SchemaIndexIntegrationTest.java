package com.claseya;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the DB-01 migration: the hot-path indexes must exist after Flyway runs.
 * A dropped or renamed index in a future migration fails here instead of silently
 * degrading the inbox and the subjects catalog.
 */
@SpringBootTest
class SchemaIndexIntegrationTest extends AbstractPostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hotPathIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertThat(indexes)
                .contains("idx_messages_unread", "idx_subjects_active_name",
                        "idx_messages_conversation_created_id");
    }
}
