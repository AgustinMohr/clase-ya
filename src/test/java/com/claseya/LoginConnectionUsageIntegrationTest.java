package com.claseya;

import com.claseya.model.User;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RT-47: the BCrypt check of a login must not run inside a database transaction. A transaction
 * holds a pooled connection, so with BCrypt slow on a small instance a handful of concurrent
 * logins exhausted the pool (10 connections, 10 s wait) and answered HTTP 500. This test is
 * deliberately NOT {@code @Transactional}: the request itself must not open one around BCrypt.
 */
class LoginConnectionUsageIntegrationTest extends AbstractWebIntegrationTest {

    @MockitoSpyBean
    private PasswordEncoder spiedEncoder;

    private User user;

    @AfterEach
    void cleanUp() {
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    private AtomicBoolean recordTransactionStateDuringPasswordCheck() {
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(spiedEncoder).matches(any(), any());
        return transactionActive;
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void successfulLoginChecksThePasswordOutsideAnyTransaction() throws Exception {
        user = createUser("rt47-ok+" + UUID.randomUUID() + "@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        AtomicBoolean transactionActive = recordTransactionStateDuringPasswordCheck();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(PASSWORD)))
                .andExpect(status().isOk());

        assertThat(transactionActive.get()).as("BCrypt ran inside a DB transaction").isFalse();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt())
                .as("last login still recorded").isNotNull();
    }

    @Test
    void failedLoginChecksThePasswordOutsideAnyTransaction() throws Exception {
        user = createUser("rt47-bad+" + UUID.randomUUID() + "@example.com", UserRole.STUDENT, UserStatus.ACTIVE);
        AtomicBoolean transactionActive = recordTransactionStateDuringPasswordCheck();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("wrong-password")))
                .andExpect(status().isUnauthorized());

        assertThat(transactionActive.get()).as("BCrypt ran inside a DB transaction").isFalse();
    }
}
