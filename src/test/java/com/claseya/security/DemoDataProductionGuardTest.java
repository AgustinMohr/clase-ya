package com.claseya.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataProductionGuardTest {

    @Mock
    private Environment environment;

    @Mock
    private DemoDataQuarantineService quarantineService;

    private DemoDataProductionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DemoDataProductionGuard(environment, quarantineService);
    }

    @Test
    void localAndTestProfilesDoNotTouchDemoAccounts() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        guard.run(null);
        verifyNoInteractions(quarantineService);

        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        guard.run(null);
        verifyNoInteractions(quarantineService);
    }

    @Test
    void productionQuarantinesDemoAccounts() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(quarantineService.quarantineDemoAccounts()).thenReturn(4);

        guard.run(null);

        verify(quarantineService).quarantineDemoAccounts();
    }

    @Test
    void productionWithoutDemoAccountsStillRunsTheCheck() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(quarantineService.quarantineDemoAccounts()).thenReturn(0);

        guard.run(null);

        verify(quarantineService).quarantineDemoAccounts();
    }
}
