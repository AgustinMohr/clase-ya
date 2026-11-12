package com.claseya.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * OPS-003: neutralizes demo accounts outside local/test.
 *
 * <p>The demo dataset uses the reserved {@code @claseya.dev} domain and the documented
 * {@code Password123}. A database seeded in development keeps those accounts even after the
 * deployment turns {@code DEMO_SEED} off, so the startup guard must remove their ability to
 * authenticate. Rows are not deleted here: deletion is an explicit operational decision.
 */
@Service
public class DemoDataQuarantineService {

    static final String DEMO_EMAIL_PATTERN = "%@claseya.dev";

    private static final String QUARANTINE_SQL = """
            update users
               set status = 'SUSPENDED',
                   password_hash = ?,
                   updated_at = now()
             where lower(email) like ?
               and status <> 'SUSPENDED'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public DemoDataQuarantineService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return how many demo accounts were neutralized by this call; zero when they were already
     * suspended or none exist.
     */
    @Transactional
    public int quarantineDemoAccounts() {
        // A random secret makes every stored hash unusable. One hash for the batch is enough:
        // none of these accounts can be recovered by guessing the discarded value.
        String unusableHash = passwordEncoder.encode(UUID.randomUUID().toString());
        return jdbcTemplate.update(QUARANTINE_SQL, unusableHash, DEMO_EMAIL_PATTERN);
    }
}
