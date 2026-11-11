package com.claseya.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataQuarantineServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DemoDataQuarantineService service;

    @BeforeEach
    void setUp() {
        service = new DemoDataQuarantineService(jdbcTemplate, passwordEncoder);
    }

    @Test
    void suspendsDemoAccountsAndReplacesTheirPasswordHash() {
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-hash");
        when(jdbcTemplate.update(anyString(), anyString(), anyString())).thenReturn(3);

        assertThat(service.quarantineDemoAccounts()).isEqualTo(3);

        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("status = 'SUSPENDED'")
                        && sql.contains("lower(email) like ?")
                        && sql.contains("status <> 'SUSPENDED'")),
                eq("unusable-hash"),
                eq(DemoDataQuarantineService.DEMO_EMAIL_PATTERN));
    }
}
