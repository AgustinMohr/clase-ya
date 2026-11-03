import { describe, expect, it } from 'vitest';
import { buildRobots } from './robots';

describe('buildRobots', () => {
  it('writes an absolute sitemap URL and disallows private routes', () => {
    const robots = buildRobots('https://claseya.test');

    expect(robots).toContain('User-agent: *');
    expect(robots).toContain('Allow: /');
    expect(robots).toContain('Disallow: /admin');
    expect(robots).toContain('Disallow: /favoritos');
    expect(robots).toContain('Disallow: /mensajes');
    expect(robots).toContain('Disallow: /mi-anuncio');
    expect(robots).toContain('Disallow: /activar');
    expect(robots).toContain('Sitemap: https://claseya.test/sitemap.xml');
  });

  it('normalizes a trailing slash and falls back to localhost', () => {
    expect(buildRobots('https://claseya.test/')).toContain(
      'Sitemap: https://claseya.test/sitemap.xml',
    );
    expect(buildRobots(undefined)).toContain('Sitemap: http://localhost:5173/sitemap.xml');
    expect(buildRobots('   ')).toContain('Sitemap: http://localhost:5173/sitemap.xml');
  });
});
