package com.claseya.security;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RT-05: last line of defence before an error event leaves the process for Sentry.
 *
 * <p>It removes everything that can identify a person or grant access: request bodies, cookies,
 * the query string (activation/reset links carry tokens), credential and proxy headers, the
 * client address, the user, and any email address inside messages or exception texts. If the
 * scrubbing itself fails the event is dropped, never sent unfiltered.
 */
@Component
public class SentryEventScrubber implements SentryOptions.BeforeSendCallback {

    private static final Logger log = LoggerFactory.getLogger(SentryEventScrubber.class);

    static final String REDACTED_EMAIL = "[email]";

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    private static final Set<String> DROPPED_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-xsrf-token", "x-csrf-token", "x-origin-secret", "x-api-key",
            "x-forwarded-for", "x-real-ip", "forwarded", "remote-addr");

    @Override
    public SentryEvent execute(SentryEvent event, Hint hint) {
        try {
            scrub(event);
            return event;
        } catch (RuntimeException ex) {
            // Do not log the event: it is the thing we could not clean.
            log.warn("Sentry event dropped because scrubbing failed: {}", ex.getClass().getName());
            return null;
        }
    }

    private void scrub(SentryEvent event) {
        event.setUser(null);
        event.setServerName(null);

        Request request = event.getRequest();
        if (request != null) {
            request.setOthers(null);
            request.setData(null);
            request.setCookies(null);
            request.setQueryString(null);
            request.setFragment(null);
            request.setEnvs(null);
            request.setHeaders(filterHeaders(request.getHeaders()));
        }

        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(redactEmails(message.getMessage()));
            message.setFormatted(redactEmails(message.getFormatted()));
            message.setParams(null);
        }

        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException exception : exceptions) {
                exception.setValue(redactEmails(exception.getValue()));
            }
        }
    }

    private static Map<String, String> filterHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> kept = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (!DROPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                kept.put(name, value);
            }
        });
        return kept;
    }

    static String redactEmails(String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll(REDACTED_EMAIL);
    }
}
