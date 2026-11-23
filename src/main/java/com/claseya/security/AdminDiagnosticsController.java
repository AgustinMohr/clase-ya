package com.claseya.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RT-05: lets an ADMIN prove, in a real environment, that an unexpected error reaches Sentry
 * (and that the event carries no personal data). The route is already ADMIN-only in
 * {@code SecurityConfig} ({@code /api/admin/**}); this controller never decides who is allowed in.
 *
 * <p>It answers 500 on purpose. It is a GET so it can be called from a browser tab that already
 * holds the admin session (no CSRF header needed); the only effect is one error event.
 */
@RestController
@RequestMapping("/api/admin/diagnostics")
public class AdminDiagnosticsController {

    @GetMapping("/error-test")
    public void errorTest() {
        throw new IntentionalDiagnosticsException();
    }

    /** Thrown on purpose by {@link #errorTest()}; it is the 500 that Sentry must receive. */
    static class IntentionalDiagnosticsException extends RuntimeException {
        IntentionalDiagnosticsException() {
            super("Intentional diagnostics error (RT-05): triggered by an admin to verify Sentry");
        }
    }
}
