package com.claseya;

import com.claseya.auth.repository.EmailVerificationTokenRepository;
import com.claseya.common.exception.ServiceUnavailableException;
import com.claseya.model.EmailVerificationToken;
import com.claseya.model.User;
import com.claseya.model.enums.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUTH-001: registration → activation link → ACTIVE account → password login.
 */
@Transactional
class AccountActivationIntegrationTest extends AbstractWebIntegrationTest {

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    private void register(String email) throws Exception {
        register(email, PASSWORD);
    }

    private void register(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s","role":"STUDENT"}
                                """.formatted(email, password)))
                .andExpect(status().isAccepted());
    }

    /** Last activation token captured from the mocked sender. */
    private String lastToken(String email) {
        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(activationMailSender, atLeastOnce()).sendActivation(org.mockito.ArgumentMatchers.eq(email), links.capture());
        String link = links.getAllValues().get(links.getAllValues().size() - 1);
        int index = link.indexOf("token=");
        assertThat(index).isGreaterThanOrEqualTo(0);
        return link.substring(index + "token=".length());
    }

    private void activate(String token) throws Exception {
        mockMvc.perform(post("/api/auth/activate")
                        .contentType("application/json")
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isNoContent());
    }

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void register_sendsLinkAndKeepsAccountPending() throws Exception {
        String email = "pending-" + UUID.randomUUID() + "@example.com";
        register(email);

        assertThat(user(email).getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(user(email).getEmailVerifiedAt()).isNull();
        assertThat(tokenRepository.findByUser_Id(user(email).getId())).hasSize(1);
        assertThat(lastToken(email)).isNotBlank();
    }

    @Test
    void register_existingPendingAccount_resendsLinkWithoutChangingPassword() throws Exception {
        String email = "re-register-" + UUID.randomUUID() + "@example.com";
        register(email);
        String firstToken = lastToken(email);

        register(email, "Different456");

        String secondToken = lastToken(email);
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(passwordEncoder.matches("Different456", user(email).getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(PASSWORD, user(email).getPasswordHash())).isTrue();
        assertThat(tokenRepository.findByUser_Id(user(email).getId())).hasSize(2);
    }

    @Test
    void activate_withValidToken_activatesAndAllowsPasswordLogin() throws Exception {
        String email = "activate-" + UUID.randomUUID() + "@example.com";
        register(email);

        activate(lastToken(email));

        assertThat(user(email).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user(email).getEmailVerifiedAt()).isNotNull();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void activate_withExpiredToken_returns400AndLeavesAccountPending() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.com";
        register(email);

        List<EmailVerificationToken> tokens = tokenRepository.findByUser_Id(user(email).getId());
        tokens.forEach(token -> token.setExpiresAt(Instant.now().minusSeconds(60)));
        tokenRepository.saveAll(tokens);

        mockMvc.perform(post("/api/auth/activate")
                        .contentType("application/json")
                        .content("""
                                {"token":"%s"}
                                """.formatted(lastToken(email))))
                .andExpect(status().isBadRequest());

        assertThat(user(email).getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void activate_withUsedToken_returns400() throws Exception {
        String email = "reuse-" + UUID.randomUUID() + "@example.com";
        register(email);
        String token = lastToken(email);

        activate(token);

        mockMvc.perform(post("/api/auth/activate")
                        .contentType("application/json")
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resend_invalidatesPreviousToken() throws Exception {
        String email = "resend-" + UUID.randomUUID() + "@example.com";
        register(email);
        String firstToken = lastToken(email);

        mockMvc.perform(post("/api/auth/resend-activation")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        String secondToken = lastToken(email);
        assertThat(secondToken).isNotEqualTo(firstToken);

        mockMvc.perform(post("/api/auth/activate")
                        .contentType("application/json")
                        .content("""
                                {"token":"%s"}
                                """.formatted(firstToken)))
                .andExpect(status().isBadRequest());

        activate(secondToken);
        assertThat(user(email).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void resend_withUnknownEmail_returns202AndDoesNotSend() throws Exception {
        mockMvc.perform(post("/api/auth/resend-activation")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted());

        verifyNoInteractions(activationMailSender);
    }

    @Test
    void activate_whenAlreadyActiveWithFreshToken_returns204() throws Exception {
        String email = "already-" + UUID.randomUUID() + "@example.com";
        register(email);
        String token = lastToken(email);

        // An admin activated the account before the user clicked the link.
        mockMvc.perform(post("/api/admin/users/" + user(email).getId() + "/activate")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isNoContent());
        assertThat(user(email).getEmailVerifiedAt()).isNull();

        activate(token);

        assertThat(user(email).getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void admin_canActivatePendingAccount() throws Exception {
        String email = "manual-" + UUID.randomUUID() + "@example.com";
        register(email);

        mockMvc.perform(post("/api/admin/users/" + user(email).getId() + "/activate")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isNoContent());

        assertThat(user(email).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user(email).getEmailVerifiedAt()).isNull();
    }

    @Test
    void admin_activateUnknownUser_returns404() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + UUID.randomUUID() + "/activate")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isNotFound());
    }

    /**
     * No SMTP configured and no development fallback: the request fails and the account is not
     * created, so nobody is left with an unusable PENDING account. Runs without the test
     * transaction so the service rollback is actually observable.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void register_withoutMailTransport_returns503AndRollsBack() throws Exception {
        String email = "nomail-" + UUID.randomUUID() + "@example.com";
        doThrow(new ServiceUnavailableException("Email delivery is not configured"))
                .when(activationMailSender).sendActivation(anyString(), anyString());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"%s","role":"STUDENT"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isServiceUnavailable());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }
}
