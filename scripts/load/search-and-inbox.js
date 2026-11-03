/**
 * ClaseYa load smoke (TEST-01).
 *
 * Measures the two hot read paths:
 *   - public teacher search (`GET /api/teachers`)
 *   - the authenticated inbox (`GET /api/conversations`)
 *
 * The session is created once in setup() and reused by every VU: logging in per VU would hit the
 * login/account rate limit and measure the limiter instead of the read paths.
 *
 * Run through scripts/load.ps1. The default target is the local backend; for a bigger dataset set
 * BASE_URL to the deployment/staging URL that has the 5k-teacher fixture.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL || 'student@claseya.dev';
const PASSWORD = __ENV.PASSWORD || 'Password123';
const DURATION = __ENV.DURATION || '30s';
const SEARCH_VUS = Number(__ENV.VUS || 10);
const INBOX_VUS = Number(__ENV.INBOX_VUS || 5);

export const options = {
  scenarios: {
    search: {
      executor: 'constant-vus',
      vus: SEARCH_VUS,
      duration: DURATION,
      exec: 'search',
      tags: { scenario: 'search' },
    },
    inbox: {
      executor: 'constant-vus',
      vus: INBOX_VUS,
      duration: DURATION,
      exec: 'inbox',
      tags: { scenario: 'inbox' },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:search}': ['p(95)<800'],
    'http_req_duration{endpoint:inbox}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

function sessionCookie(response) {
  const raw = response.headers['Set-Cookie'] || '';
  const values = Array.isArray(raw) ? raw : [raw];
  for (const value of values) {
    const match = /CLASEYA_SESSION=([^;]+)/.exec(value);
    if (match) {
      return match[1];
    }
  }
  return null;
}

export function setup() {
  const csrf = http.get(`${BASE_URL}/api/auth/csrf`);
  if (csrf.status !== 200) {
    throw new Error(`CSRF fetch failed: ${csrf.status}`);
  }

  const login = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrf.json('token'),
      },
    },
  );
  if (login.status !== 200) {
    throw new Error(`Login failed for ${EMAIL}: ${login.status} ${login.body}`);
  }

  const session = sessionCookie(login);
  if (!session) {
    throw new Error('Login response did not set CLASEYA_SESSION');
  }
  return { session };
}

export function search() {
  const response = http.get(`${BASE_URL}/api/teachers?page=0&size=20`, {
    tags: { endpoint: 'search' },
  });
  check(response, { 'search 200': (res) => res.status === 200 });
  sleep(0.5);
}

export function inbox(data) {
  const response = http.get(`${BASE_URL}/api/conversations?page=0&size=20`, {
    headers: { Cookie: `CLASEYA_SESSION=${data.session}` },
    tags: { endpoint: 'inbox' },
  });
  check(response, { 'inbox 200': (res) => res.status === 200 });
  sleep(0.5);
}
