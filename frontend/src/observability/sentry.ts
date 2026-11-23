import * as Sentry from '@sentry/react';
import type { ErrorEvent } from '@sentry/react';

/**
 * RT-05: browser error tracking. It is inert unless VITE_SENTRY_DSN is set at build time, and it
 * never sends personal data: no user, no cookies, no query strings or fragments (activation and
 * reset links carry tokens there), no breadcrumbs, no tracing and no session replay.
 */

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;

export function redactEmails(text: string | undefined): string | undefined {
  return text?.replace(EMAIL, '[email]');
}

/** Drops the query string and fragment of a URL; falls back to a cut when it cannot be parsed. */
export function stripUrl(url: string | undefined): string | undefined {
  if (!url) return url;
  try {
    const parsed = new URL(url);
    return `${parsed.origin}${parsed.pathname}`;
  } catch {
    return url.split(/[?#]/)[0];
  }
}

/** beforeSend hook. Exported for tests. Returning null drops the event. */
export function scrubEvent(event: ErrorEvent): ErrorEvent | null {
  try {
    delete event.user;
    delete event.server_name;
    if (event.request) {
      event.request.url = stripUrl(event.request.url);
      delete event.request.query_string;
      delete event.request.cookies;
      delete event.request.data;
      if (event.request.headers) {
        const headers: Record<string, string> = {};
        for (const [name, value] of Object.entries(event.request.headers)) {
          headers[name] = name.toLowerCase() === 'referer' ? (stripUrl(value) ?? '') : value;
        }
        event.request.headers = headers;
      }
    }
    event.exception?.values?.forEach((exception) => {
      exception.value = redactEmails(exception.value);
    });
    if (event.message) event.message = redactEmails(event.message);
    delete event.breadcrumbs;
    return event;
  } catch {
    // Never send an event that could not be cleaned.
    return null;
  }
}

export function initSentry(): void {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'production',
    release: import.meta.env.VITE_VERCEL_GIT_COMMIT_SHA || undefined,
    tracesSampleRate: 0,
    maxBreadcrumbs: 0,
    // Browser noise that is not an application bug.
    ignoreErrors: [
      'ResizeObserver loop limit exceeded',
      'ResizeObserver loop completed with undelivered notifications.',
    ],
    denyUrls: [/^chrome-extension:\/\//i, /^moz-extension:\/\//i, /^safari-extension:\/\//i],
    beforeSend: scrubEvent,
  });
}

/** Reports a render error caught by the ErrorBoundary. A no-op when Sentry is not initialised. */
export function reportRenderError(error: Error, componentStack: string | null | undefined): void {
  Sentry.captureException(error, {
    contexts: { react: { componentStack: componentStack ?? '' } },
  });
}
