package com.claseya;

import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.User;
import com.claseya.model.enums.NotificationEventType;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.notification.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PasswordResetIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    private void forgot(String email) throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
    }

    private void reset(String token, String password) throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}
                                """.formatted(token, password)))
                .andExpect(status().isNoContent());
    }

    @Test
    void activeUser_canResetPasswordWithASingleUseToken() throws Exception {
        createUser("reset@example.com", UserRole.STUDENT, UserStatus.ACTIVE);

        forgot("reset@example.com");

        NotificationOutboxEntry entry = outboxRepository.findAll().stream()
                .filter(row -> row.getEventType() == NotificationEventType.PASSWORD_RESET)
                .findFirst()
                .orElseThrow();
        String token = String.valueOf(entry.getPayload().get("token"));

        reset(token, "NewPassword123");

        User stored = userRepository.findByEmail("reset@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPassword123", stored.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isFalse();

        // Single use: the same token cannot reset again.
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"OtherPassword123"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEmail_returns202AndDoesNotEnqueueAnything() throws Exception {
        long before = outboxRepository.count();

        forgot("missing@example.com");

        assertThat(outboxRepository.count()).isEqualTo(before);
    }

    @Test
    void pendingOrSuspendedAccounts_doNotReceiveAResetLink() throws Exception {
        createUser("pending-reset@example.com", UserRole.STUDENT, UserStatus.PENDING);
        createUser("suspended-reset@example.com", UserRole.STUDENT, UserStatus.SUSPENDED);

        forgot("pending-reset@example.com");
        forgot("suspended-reset@example.com");

        assertThat(outboxRepository.findAll().stream()
                .filter(row -> row.getEventType() == NotificationEventType.PASSWORD_RESET)
                .toList()).isEmpty();
    }

    @Test
    void invalidToken_returnsTheSameGenericBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"not-a-real-token","newPassword":"NewPassword123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weakNewPassword_isRejectedBeforeConsumingTheToken() throws Exception {
        createUser("weak-reset@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        forgot("weak-reset@example.com");
        String token = outboxRepository.findAll().stream()
                .filter(row -> row.getEventType() == NotificationEventType.PASSWORD_RESET)
                .findFirst()
                .orElseThrow()
                .getPayload()
                .get("token")
                .toString();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"short"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }
}
