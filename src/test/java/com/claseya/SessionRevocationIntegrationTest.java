package com.claseya;

import com.claseya.auth.service.PasswordResetService;
import com.claseya.auth.repository.PasswordResetTokenRepository;
import com.claseya.common.util.Hashes;
import com.claseya.model.PasswordResetToken;
import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AUTH-004: logout and password reset revoke JWTs issued before the event. */
@Transactional
class SessionRevocationIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordResetService passwordResetService;

    @Test
    void logoutInvalidatesTheOldTokenAndANewLoginWorks() throws Exception {
        User user = createUser("logout@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String token = bearer(user);
        getJson("/api/auth/me", token, 200);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", token))
                .andExpect(status().isNoContent());

        entityManager.clear();
        getJson("/api/auth/me", token, 401);

        // A token issued after logout carries the new session version.
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        getJson("/api/auth/me", bearer(reloaded), 200);
    }

    @Test
    void passwordResetInvalidatesPreviousSessions() throws Exception {
        User user = createUser("reset@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        String token = bearer(user);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(Hashes.sha256Hex("raw-reset-token"));
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));
        passwordResetTokenRepository.saveAndFlush(resetToken);

        passwordResetService.reset("raw-reset-token", "NewPassword123");

        entityManager.clear();
        getJson("/api/auth/me", token, 401);
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        getJson("/api/auth/me", bearer(reloaded), 200);
    }
}
