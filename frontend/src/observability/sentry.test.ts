import * as Sentry from '@sentry/react';
import type { ErrorEvent } from '@sentry/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { initSentry, redactEmails, scrubEvent, stripUrl } from './sentry';

function baseEvent(overrides: Partial<ErrorEvent> = {}): ErrorEvent {
  return { type: undefined, ...overrides } as ErrorEvent;
}

describe('initSentry', () => {
  afterEach(async () => {
    await Sentry.close();
    vi.unstubAllEnvs();
  });

  it('tells Sentry never to infer the visitor IP address', () => {
    vi.stubEnv('VITE_SENTRY_DSN', 'https://publickey@o0.ingest.example.invalid/1');

    initSentry();

    const options = Sentry.getClient()!.getOptions();
    expect(options._metadata?.sdk?.settings?.infer_ip).toBe('never');
  });
});

describe('stripUrl', () => {
  it('drops the query string and fragment', () => {
    expect(stripUrl('https://clase-ya.vercel.app/activar?token=abc#x')).toBe(
      'https://clase-ya.vercel.app/activar',
    );
  });

  it('cuts relative or unparseable values at the first ? or #', () => {
    expect(stripUrl('/reset?token=abc')).toBe('/reset');
  });
});

describe('redactEmails', () => {
  it('replaces every email address', () => {
    expect(redactEmails('a@b.com and c.d+e@f.org')).toBe('[email] and [email]');
  });
});

describe('scrubEvent', () => {
  it('removes user, cookies, data, query string and breadcrumbs, and cleans the URLs', () => {
    const event = baseEvent({
      user: { email: 'jane@example.com' },
      breadcrumbs: [{ message: 'click' }],
      request: {
        url: 'https://clase-ya.vercel.app/activar?token=secret',
        query_string: 'token=secret',
        cookies: { a: 'b' },
        data: { password: 'x' },
        headers: {
          Referer: 'https://clase-ya.vercel.app/reset?token=secret',
          'User-Agent': 'JUnit',
        },
      },
    });

    const result = scrubEvent(event)!;

    expect(result.user).toBeUndefined();
    expect(result.breadcrumbs).toBeUndefined();
    expect(result.request?.url).toBe('https://clase-ya.vercel.app/activar');
    expect(result.request?.query_string).toBeUndefined();
    expect(result.request?.cookies).toBeUndefined();
    expect(result.request?.data).toBeUndefined();
    expect(result.request?.headers?.Referer).toBe('https://clase-ya.vercel.app/reset');
    expect(result.request?.headers?.['User-Agent']).toBe('JUnit');
  });

  it('redacts emails in exception texts and messages', () => {
    const event = baseEvent({
      message: 'No se pudo enviar a jane@example.com',
      exception: { values: [{ type: 'Error', value: 'Usuario bob@mail.co no existe' }] },
    });

    const result = scrubEvent(event)!;

    expect(result.message).toBe('No se pudo enviar a [email]');
    expect(result.exception?.values?.[0].value).toBe('Usuario [email] no existe');
  });

  it('drops the event when it cannot be cleaned', () => {
    const event = baseEvent();
    Object.defineProperty(event, 'request', {
      get() {
        throw new Error('boom');
      },
    });

    expect(scrubEvent(event)).toBeNull();
  });
});
