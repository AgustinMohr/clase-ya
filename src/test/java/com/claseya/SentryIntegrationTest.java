package com.claseya;

import com.claseya.model.NotificationOutboxEntry;
import com.claseya.model.enums.NotificationEventType;
import com.claseya.model.enums.UserRole;
import com.claseya.model.enums.UserStatus;
import com.claseya.notification.repository.NotificationOutboxRepository;
import com.claseya.notification.service.NotificationOutboxWorker;
import com.claseya.security.SentryEventScrubber;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.ITransportFactory;
import io.sentry.NoOpTransportFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RT-05: with a DSN configured, an unexpected 500 produces exactly one event and it is scrubbed;
 * handled 4xx responses produce none. The transport is a no-op, so nothing leaves the test.
 */
@SpringBootTest(properties = {
        // @SpringBootTest on a subclass replaces the parent attributes, so they are repeated here.
        "app.demo-seed.enabled=false",
        "security.rate-limit.enabled=false",
        "security.csrf.enabled=false",
        "sentry.dsn=https://publickey@o0.ingest.example.invalid/1",
        "sentry.environment=test"
})
@Import(SentryIntegrationTest.BoomConfig.class)
@Transactional
class SentryIntegrationTest extends AbstractWebIntegrationTest {

    @MockitoSpyBean
    private SentryEventScrubber scrubber;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private NotificationOutboxWorker outboxWorker;

    @BeforeEach
    void resetSpy() {
        clearInvocations(scrubber);
    }

    @Test
    void unexpectedErrorIsReportedOnceAndScrubbed() throws Exception {
        mockMvc.perform(get("/api/auth/boom")
                        .queryParam("token", "secret-activation-token")
                        .header("X-Origin-Secret", "shared-secret")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .header("Cookie", "CLASEYA_SESSION=jwt"))
                .andExpect(status().isInternalServerError());

        ArgumentCaptor<SentryEvent> captor = ArgumentCaptor.forClass(SentryEvent.class);
        verify(scrubber, timeout(5000).times(1)).execute(captor.capture(), any(Hint.class));
        SentryEvent event = captor.getValue();

        assertThat(event.getRequest()).isNotNull();
        assertThat(event.getRequest().getQueryString()).isNull();
        assertThat(event.getRequest().getCookies()).isNull();
        assertThat(event.getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("x-origin-secret")
                        || name.equalsIgnoreCase("x-forwarded-for")
                        || name.equalsIgnoreCase("cookie"));
        assertThat(String.valueOf(event.getMessage())).doesNotContain("jane@example.com");
        assertThat(event.getExceptions()).isNotEmpty();
        event.getExceptions().forEach(exception ->
                assertThat(exception.getValue()).doesNotContain("jane@example.com"));
    }

    @Test
    void handledClientErrorsAreNotReported() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"WrongPassword1\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/teachers/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());

        verify(scrubber, never()).execute(any(), any());
    }

    @Test
    void adminDiagnosticsEndpointProducesOneScrubbedEvent() throws Exception {
        String admin = adminBearer();

        mockMvc.perform(get("/api/admin/diagnostics/error-test")
                        .queryParam("token", "secret-activation-token")
                        .header("Authorization", admin))
                .andExpect(status().isInternalServerError());

        ArgumentCaptor<SentryEvent> captor = ArgumentCaptor.forClass(SentryEvent.class);
        verify(scrubber, timeout(5000).times(1)).execute(captor.capture(), any(Hint.class));
        SentryEvent event = captor.getValue();
        assertThat(event.getRequest().getQueryString()).isNull();
        assertThat(event.getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("authorization"));
        assertThat(event.getUser()).isNull();
    }

    @Test
    void adminDiagnosticsEndpointIsClosedToNonAdmins() throws Exception {
        String student = bearer(createUser("student+" + java.util.UUID.randomUUID() + "@test.com",
                UserRole.STUDENT, UserStatus.ACTIVE));

        mockMvc.perform(get("/api/admin/diagnostics/error-test"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/diagnostics/error-test")
                        .header("Authorization", student))
                .andExpect(status().isForbidden());

        verify(scrubber, never()).execute(any(), any());
    }

    @Test
    void deadOutboxRowProducesOneEvent() throws Exception {
        NotificationOutboxEntry entry = new NotificationOutboxEntry();
        entry.setEventType(NotificationEventType.ACTIVATION);
        entry.setRecipientUserId(createUser("dead+" + java.util.UUID.randomUUID() + "@test.com",
                UserRole.STUDENT, UserStatus.PENDING).getId());
        entry.setDedupeKey("sentry-dead-" + java.util.UUID.randomUUID());
        // No token: a permanent failure, so the worker marks the row DEAD on the first attempt.
        entry.setPayload(java.util.Map.of("email", "jane@example.com"));
        // now() in the database is the start of this test transaction.
        entry.setNextAttemptAt(java.time.Instant.now().minusSeconds(3600));
        outboxRepository.saveAndFlush(entry);

        outboxWorker.processDue();

        ArgumentCaptor<SentryEvent> captor = ArgumentCaptor.forClass(SentryEvent.class);
        verify(scrubber, timeout(5000).times(1)).execute(captor.capture(), any(Hint.class));
        assertThat(captor.getValue().getMessage().getFormatted())
                .contains(entry.getId().toString())
                .contains("is DEAD")
                .doesNotContain("jane@example.com");
    }

    @TestConfiguration
    static class BoomConfig {
        @Bean
        ITransportFactory noOpTransportFactory() {
            return NoOpTransportFactory.getInstance();
        }

        @Bean
        BoomController boomController() {
            return new BoomController();
        }
    }

    @RestController
    static class BoomController {
        @GetMapping("/api/auth/boom")
        String boom() {
            throw new IllegalStateException("Cannot notify jane@example.com");
        }
    }
}
