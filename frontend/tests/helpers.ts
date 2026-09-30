import { request, type APIRequestContext, type Page } from '@playwright/test';

export const API_URL = 'http://localhost:8080';
export const PASSWORD = 'Password123';
export const STUDENT = 'student@claseya.dev';
export const TEACHER = 'ana.profe@claseya.dev';

/** Key used by the app to keep the JWT in sessionStorage. */
const TOKEN_KEY = 'claseya.token';

export interface TeacherRef {
  id: string;
  displayName: string;
}

/** Unique text per run, so assertions never depend on leftovers from a previous one. */
export function uniqueText(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}

export async function apiToken(email: string = STUDENT): Promise<string> {
  const context = await request.newContext({ baseURL: API_URL });
  try {
    const response = await context.post('/api/auth/login', { data: { email, password: PASSWORD } });
    if (!response.ok()) {
      throw new Error(`Login failed for ${email}: ${response.status()} ${await response.text()}`);
    }
    const { accessToken } = (await response.json()) as { accessToken: string };
    return accessToken;
  } finally {
    await context.dispose();
  }
}

export async function apiContext(token: string): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  });
}

/**
 * Signs in through the API and injects the token before the app boots: deterministic, fast,
 * and it never touches Google.
 */
export async function loginAs(page: Page, email: string = STUDENT): Promise<string> {
  const token = await apiToken(email);
  await page.addInitScript(
    ([key, value]) => window.sessionStorage.setItem(key, value),
    [TOKEN_KEY, token],
  );
  return token;
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
  token: string,
  teacherId: string,
  firstMessage?: string,
): Promise<string> {
  const context = await apiContext(token);
  try {
    const response = await context.post('/api/conversations', { data: { teacherId } });
    if (!response.ok()) {
      throw new Error(`Starting the conversation failed: ${response.status()} ${await response.text()}`);
    }
    const { id } = (await response.json()) as { id: string };
    if (firstMessage) {
      const sent = await context.post(`/api/conversations/${id}/messages`, {
        data: { content: firstMessage },
      });
      if (!sent.ok()) {
        throw new Error(`Sending the precondition message failed: ${sent.status()}`);
      }
    }
    return id;
  } finally {
    await context.dispose();
  }
}
