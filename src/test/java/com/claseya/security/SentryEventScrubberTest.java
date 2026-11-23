package com.claseya.security;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

class SentryEventScrubberTest {

    private final SentryEventScrubber scrubber = new SentryEventScrubber();

    @Test
    void removesRequestDataThatCouldIdentifyOrAuthenticate() {
        SentryEvent event = new SentryEvent();
        Request request = new Request();
        request.setUrl("https://clase-ya-api.onrender.com/api/auth/activate");
        request.setQueryString("token=secret-activation-token");
        request.setCookies("CLASEYA_SESSION=jwt");
        request.setData("{\"password\":\"Password123\"}");
        request.setHeaders(Map.of(
                "Cookie", "CLASEYA_SESSION=jwt",
                "Authorization", "Bearer abc",
                "X-XSRF-TOKEN", "csrf",
                "X-Origin-Secret", "shared",
                "X-Forwarded-For", "203.0.113.9",
                "User-Agent", "JUnit",
                "Accept", "application/json"));
        event.setRequest(request);
        User user = new User();
        user.setEmail("jane@example.com");
        event.setUser(user);

        SentryEvent result = scrubber.execute(event, new Hint());

        assertThat(result).isSameAs(event);
        assertThat(result.getUser()).isNull();
        assertThat(result.getRequest().getQueryString()).isNull();
        assertThat(result.getRequest().getCookies()).isNull();
        assertThat(result.getRequest().getData()).isNull();
        assertThat(result.getRequest().getUrl()).endsWith("/api/auth/activate");
        assertThat(result.getRequest().getHeaders()).containsOnlyKeys("User-Agent", "Accept");
    }

    @Test
    void redactsEmailsInMessagesAndExceptionTexts() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("Could not send to jane@example.com");
        message.setFormatted("Could not send to jane@example.com");
        message.setParams(List.of("jane@example.com"));
        event.setMessage(message);
        SentryException exception = new SentryException();
        exception.setValue("No user bob.smith+tag@mail.co.uk found");
        event.setExceptions(List.of(exception));

        SentryEvent result = scrubber.execute(event, new Hint());

        assertThat(result.getMessage().getFormatted()).isEqualTo("Could not send to [email]");
        assertThat(result.getMessage().getMessage()).isEqualTo("Could not send to [email]");
        assertThat(result.getMessage().getParams()).isNull();
        assertThat(result.getExceptions().get(0).getValue()).isEqualTo("No user [email] found");
    }

    @Test
    void keepsEventsWithoutRequestOrMessage() {
        SentryEvent event = new SentryEvent();

        assertThat(scrubber.execute(event, new Hint())).isSameAs(event);
    }

    @Test
    void dropsTheEventWhenScrubbingFails() {
        SentryEvent event = spy(new SentryEvent());
        doThrow(new IllegalStateException("boom")).when(event).getRequest();

        assertThat(scrubber.execute(event, new Hint())).isNull();
    }
}
