import { request, type APIRequestContext, type Page } from '@playwright/test';

export const API_URL = 'http://localhost:8080';
export const PASSWORD = 'Password123';
export const STUDENT = 'student@claseya.dev';
export const TEACHER = 'ana.profe@claseya.dev';

export interface TeacherRef {
  id: string;
  displayName: string;
}

/** Unique text per run, so assertions never depend on leftovers from a previous one. */
export function uniqueText(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

/** Fetches a CSRF token and stores its cookie in the browser context's jar. */
async function csrfToken(context: APIRequestContext): Promise<string> {
  const response = await context.get('/api/auth/csrf');
  if (!response.ok()) {
    throw new Error(`CSRF token failed: ${response.status()} ${await response.text()}`);
  }
  return ((await response.json()) as { token: string }).token;
}

/**
 * Signs in through the API so the browser context receives the HttpOnly session cookie.
 * The SPA never sees a token: it resolves the user from `/api/auth/me` (SEC-002).
 */
export async function loginAs(page: Page, email: string = STUDENT): Promise<void> {
  const context = page.context().request;
  const token = await csrfToken(context);
  const response = await context.post('/api/auth/login', {
    data: { email, password: PASSWORD },
    headers: { 'X-XSRF-TOKEN': token },
  });
  if (!response.ok()) {
    throw new Error(`Login failed for ${email}: ${response.status()} ${await response.text()}`);
  }
}

/** Teachers of the public listing, in the same order the landing cards show them. */
export async function publicTeachers(limit = 1): Promise<TeacherRef[]> {
  const context = await request.newContext({ baseURL: API_URL });
  try {
    const response = await context.get(`/api/teachers?page=0&size=${limit}`);
    if (!response.ok()) {
      throw new Error(`Teacher listing failed: ${response.status()}`);
    }
    const page = (await response.json()) as { content: TeacherRef[] };
    if (page.content.length === 0) {
      throw new Error('No public teachers: the demo seed did not run');
    }
    return page.content;
  } finally {
    await context.dispose();
  }
}

/** First teacher of the public listing, which is also the first card on the landing. */
export async function firstPublicTeacher(): Promise<TeacherRef> {
  return (await publicTeachers(1))[0];
}

/** Creates (or reuses) the student's conversation with a teacher and returns its id. */
export async function ensureConversationWith(
  page: Page,
  teacherId: string,
  firstMessage?: string,
): Promise<string> {
  const context = page.context().request;
  const token = await csrfToken(context);
  const response = await context.post('/api/conversations', {
    data: { teacherId },
    headers: { 'X-XSRF-TOKEN': token },
  });
  if (!response.ok()) {
    throw new Error(`Starting the conversation failed: ${response.status()} ${await response.text()}`);
  }
  const { id } = (await response.json()) as { id: string };
  if (firstMessage) {
    const messageToken = await csrfToken(context);
    const sent = await context.post(`/api/conversations/${id}/messages`, {
      data: { content: firstMessage },
      headers: { 'X-XSRF-TOKEN': messageToken },
    });
    if (!sent.ok()) {
      throw new Error(`Sending the precondition message failed: ${sent.status()}`);
    }
  }
  return id;
}
