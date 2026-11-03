import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, resetCsrfToken } from './api';

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

function noContent(): Response {
  return new Response(null, { status: 204 });
}

function callHeaders(mock: ReturnType<typeof vi.fn>, index: number): Record<string, string> {
  return (mock.mock.calls[index][1] as RequestInit).headers as Record<string, string>;
}

async function captureError(run: () => Promise<unknown>): Promise<ApiError> {
  try {
    await run();
  } catch (caught) {
    expect(caught).toBeInstanceOf(ApiError);
    return caught as ApiError;
  }
  throw new Error('Expected the request to fail');
}

describe('api transport', () => {
  beforeEach(() => {
    resetCsrfToken();
    vi.unstubAllGlobals();
  });

  it('fetches the CSRF token once and reuses it for later writes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 't1' }))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await api.addFavorite('a');
    await api.addFavorite('b');

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toContain('/api/favorites/a');
    expect(callHeaders(fetchMock, 1)['X-XSRF-TOKEN']).toBe('t1');
    expect(callHeaders(fetchMock, 2)['X-XSRF-TOKEN']).toBe('t1');
  });

  it('does not request a CSRF token for reads', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(json({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);

    await api.popularSubjects();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });
    expect(fetchMock.mock.calls[0][0]).toContain('/api/subjects/popular?limit=12');
  });

  it('deduplicates concurrent CSRF fetches for parallel writes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 't1' }))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await Promise.all([api.addFavorite('a'), api.addFavorite('b')]);

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(callHeaders(fetchMock, 1)['X-XSRF-TOKEN']).toBe('t1');
    expect(callHeaders(fetchMock, 2)['X-XSRF-TOKEN']).toBe('t1');
  });

  it('requests a thread page with the keyset cursor and no CSRF token', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(
      json({
        id: 'c1',
        otherParticipant: { id: 'u1', displayName: 'Ana', role: 'TEACHER' },
        unreadCount: 0,
        updatedAt: '2026-11-03T12:00:00Z',
        messages: [],
        hasMore: true,
        nextBefore: 'm1',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const thread = await api.conversationThread('c1', 'm1', 25);

    expect(thread.nextBefore).toBe('m1');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toContain(
      '/api/conversations/c1/thread?limit=25&before=m1',
    );
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('GET');
    expect(callHeaders(fetchMock, 0)['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('asks the server for catalog suggestions instead of filtering a local copy', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(json({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }));
    vi.stubGlobal('fetch', fetchMock);

    await api.subjectSearch('álgebra lineal', 10);

    const url = fetchMock.mock.calls[0][0] as string;
    expect(url).toContain('/api/subjects?query=%C3%A1lgebra%20lineal');
    expect(url).toContain('size=10');
  });

  it('refreshes a stale token and retries the write once on 403', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 'old' }))
      .mockResolvedValueOnce(json({ message: 'Access denied' }, 403))
      .mockResolvedValueOnce(json({ token: 'new' }))
      .mockResolvedValueOnce(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await api.addFavorite('a');

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(callHeaders(fetchMock, 3)['X-XSRF-TOKEN']).toBe('new');
  });

  it('exposes Retry-After on a 429 instead of swallowing it', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 't1' }))
      .mockResolvedValueOnce(json({ message: 'Too Many Requests' }, 429, { 'Retry-After': '42' }));
    vi.stubGlobal('fetch', fetchMock);

    const error = await captureError(() => api.addFavorite('a'));

    expect(error.status).toBe(429);
    expect(error.retryAfterSeconds).toBe(42);
  });

  it('turns a network failure into an ApiError with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await captureError(() => api.popularSubjects());

    expect(error.status).toBe(0);
  });

  it('exchanges a message link for a session', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 't1' }))
      .mockResolvedValueOnce(json({ user: { id: 'u1', role: 'TEACHER' }, expiresIn: 3600 }));
    vi.stubGlobal('fetch', fetchMock);

    const session = await api.exchangeMessageLink('link-token');

    expect(session.user.id).toBe('u1');
    expect(fetchMock.mock.calls[1][0]).toContain('/api/auth/message-link');
    expect(String((fetchMock.mock.calls[1][1] as RequestInit).body)).toContain('link-token');
  });

  it('resolves undefined for 204 responses', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json({ token: 't1' }))
      .mockResolvedValueOnce(noContent());
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.removeFavorite('a')).resolves.toBeUndefined();
  });
});
