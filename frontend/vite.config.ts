import react from '@vitejs/plugin-react';
import { defineConfig, loadEnv, type Plugin } from 'vite';

/**
 * SEC-002: injects the Content-Security-Policy as a meta tag. Development needs inline/eval
 * for Vite's React Refresh preamble; the production policy forbids both and is verified by
 * scripts/check-csp.mjs after the build. frame-ancestors is set by the host headers because a
 * meta tag cannot enforce it (see frontend/public/_headers).
 */
function cspPlugin(dev: boolean, apiOrigin: string): Plugin {
  const connect = ["'self'", 'https://accounts.google.com', apiOrigin, dev ? 'ws:' : '']
    .filter(Boolean)
    .join(' ');
  const script = [
    "'self'",
    'https://accounts.google.com',
    dev ? "'unsafe-inline' 'unsafe-eval'" : '',
  ]
    .filter(Boolean)
    .join(' ');
  const directives = [
    "default-src 'self'",
    "base-uri 'self'",
    "object-src 'none'",
    "form-action 'self'",
    `script-src ${script}`,
    "style-src 'self' 'unsafe-inline' https://accounts.google.com",
    "img-src 'self' data: https:",
    "font-src 'self' data:",
    `connect-src ${connect}`,
    'frame-src https://accounts.google.com',
    "manifest-src 'self'",
    "worker-src 'self'",
  ].join('; ');

  return {
    name: 'claseya-csp',
    transformIndexHtml(html) {
      return html.replace(
        '<!-- CSP -->',
        `<meta http-equiv="Content-Security-Policy" content="${directives}" />`,
      );
    },
  };
}

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiOrigin = (env.VITE_API_URL ?? '').replace(/\/+$/, '');

  return {
    plugins: [react(), cspPlugin(command === 'serve', apiOrigin)],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
