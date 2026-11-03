package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.security.AppUserDetails;
import com.claseya.security.JwtService;
import com.claseya.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PERF-01 / ADR-028: measures the authenticated request path. Option A deliberately keeps the
 * per-request user reload (immediate revocation), so the budget is one {@code User} load and one
 * statement per request. A service that re-queries the user would exceed it.
 */
@SpringBootTest(properties = {
        "app.demo-seed.enabled=false",
        "security.rate-limit.enabled=false",
        "security.csrf.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        // Keep the metrics available to the assertions without logging 100 session summaries.
        "spring.jpa.properties.hibernate.session.events.log=false"
})
@AutoConfigureMockMvc
class UserLookupBudgetIntegrationTest extends AbstractPostgresTest {

    private static final int REQUESTS = 100;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void oneHundredAuthenticatedRequestsLoadTheUserOnceEach() throws Exception {
        User user = new User();
        user.setEmail("lookup-budget-" + UUID.randomUUID() + "@example.com");
        user.setName("Lookup Budget");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        String bearer = "Bearer " + jwtService.generateToken(new AppUserDetails(user));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        for (int i = 0; i < REQUESTS; i++) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(user.getId().toString()));
        }

        assertThat(statistics.getEntityLoadCount())
                .as("one User load per authenticated request; no service re-queries")
                .isEqualTo(REQUESTS);
        assertThat(statistics.getPrepareStatementCount())
                .as("one SELECT per authenticated request")
                .isEqualTo(REQUESTS);
    }
}
