/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  /** Deployed backend origin (e.g. https://api.example.com). Empty in dev (Vite proxy). */
  readonly VITE_API_URL?: string;
  /** RT-05: Sentry DSN of the web project. Empty = error tracking off. Public by design. */
  readonly VITE_SENTRY_DSN?: string;
  readonly VITE_SENTRY_ENVIRONMENT?: string;
  /** Set by Vercel when "Automatically expose System Environment Variables" is on. */
  readonly VITE_VERCEL_GIT_COMMIT_SHA?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
