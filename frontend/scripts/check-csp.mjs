import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function fail(message) {
  console.error(`CSP check failed: ${message}`);
  process.exit(1);
}

const html = readFileSync(resolve('dist/index.html'), 'utf8');
const metas = html.match(/<meta http-equiv="Content-Security-Policy" content="([^"]+)"/gi) ?? [];
if (metas.length !== 1) fail(`expected exactly one CSP meta tag, found ${metas.length}`);

const csp = metas[0].match(/content="([^"]+)"/)[1];
const directives = new Map(
  csp.split(';').map((directive) => {
    const [name, ...values] = directive.trim().split(/\s+/);
    return [name, values.join(' ')];
  }),
);

const script = directives.get('script-src');
if (!script) fail('script-src is missing');
if (script.includes("'unsafe-inline'") || script.includes("'unsafe-eval'")) {
  fail('production script-src must not allow unsafe-inline or unsafe-eval');
}
if (!script.includes("'self'") || !script.includes('https://accounts.google.com')) {
  fail('script-src must allow self and Google Identity');
}

for (const required of ["default-src 'self'", "object-src 'none'", "form-action 'self'", 'frame-src https://accounts.google.com']) {
  if (!csp.includes(required)) fail(`missing directive: ${required}`);
}

if (/<script(?![^>]*\bsrc=)[^>]*>/i.test(html)) {
  fail('production HTML contains an inline script');
}

console.log('CSP check: OK');
