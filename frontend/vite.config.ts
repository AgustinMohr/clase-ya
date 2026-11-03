import react from '@vitejs/plugin-react';
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, loadEnv, type Plugin } from 'vite';
import { buildRobots } from './src/seo/robots.ts';

/**
 * SEC-002: injects the Content-Security-Policy as a meta tag. Development needs inline/eval
 * for Vite's React Refresh preamble; the production policy forbids both and is verified by
 * scripts/check-csp.mjs after the build. frame-ancestors is set by the host headers because a
 * meta tag cannot enforce it (see frontend/public/_headers).
 */
function cspPlugin(dev: boolean, apiOrigin: string): Plugin {
  const connect = [
    "'self'",
    'https://accounts.google.com',
    'https://challenges.cloudflare.com',
    apiOrigin,
    dev ? 'ws:' : '',
  ]
    .filter(Boolean)
    .join(' ');
  const script = [
    "'self'",
    'https://accounts.google.com',
    'https://challenges.cloudflare.com',
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
    "style-src 'self' 'unsafe-inline' https://accounts.google.com https://challenges.cloudflare.com",
    "img-src 'self' data: https:",
    "font-src 'self' data:",
    `connect-src ${connect}`,
    'frame-src https://accounts.google.com https://challenges.cloudflare.com',
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
  const siteUrl =
    env.VITE_SITE_URL ||
    (env.VERCEL_PROJECT_PRODUCTION_URL ? `https://${env.VERCEL_PROJECT_PRODUCTION_URL}` : '');

  return {
    plugins: [react(), cspPlugin(command === 'serve', apiOrigin), robotsPlugin(siteUrl)],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        // SEO-001: sitemap and robots live on the backend, not in the SPA bundle.
        '/sitemap.xml': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/sitemaps': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});

/** SEO-001: Vite owns the build, so it can write robots.txt with the deployment origin. */
function robotsPlugin(siteUrl: string): Plugin {
  let outDir = 'dist';
  return {
    name: 'claseya-robots',
    apply: 'build',
    configResolved(config) {
      outDir = config.build.outDir;
    },
    closeBundle() {
      writeFileSync(resolve(outDir, 'robots.txt'), buildRobots(siteUrl), 'utf8');
    },
  };
}
