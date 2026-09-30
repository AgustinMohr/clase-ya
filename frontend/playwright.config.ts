import { defineConfig, devices } from '@playwright/test';

const BACKEND_URL = 'http://localhost:8080';
const FRONTEND_URL = 'http://localhost:5173';

/**
 * E2E suite. It drives the real stack (Spring Boot + PostgreSQL + Vite) against the local
 * demo database, so the flows are exercised exactly as a user would hit them.
 */
export default defineConfig({
  testDir: './tests',
  // The tests mutate shared state (profile, conversations): run them one at a time.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: FRONTEND_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    locale: 'es-AR',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // The suite starts its OWN backend on purpose: reusing an already running one is how
      // a stale JVM (from before a change) made the UI look broken while the tests passed.
      // E2E_REUSE_BACKEND=true is the fast path for the daily loop, and it only makes sense
      // when the running backend already has the current code compiled.
      command:
        'mvn -q spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Duser.timezone=UTC"',
      cwd: '..',
      url: `${BACKEND_URL}/api/test/public`,
      reuseExistingServer: process.env.E2E_REUSE_BACKEND === 'true',
      timeout: 240_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      // Vite serves straight from disk, so reusing a running dev server is safe.
      command: 'npm run dev',
      url: FRONTEND_URL,
      reuseExistingServer: true,
      timeout: 120_000,
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
