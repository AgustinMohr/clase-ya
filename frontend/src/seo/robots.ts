/**
 * SEO-001: robots.txt is generated at build time because the Sitemap directive requires an
 * absolute URL and the Vercel domain is only known then.
 */
export function buildRobots(siteUrl: string | undefined): string {
  const base = (siteUrl ?? '').trim().replace(/\/+$/, '') || 'http://localhost:5173';
  return [
    'User-agent: *',
    'Allow: /',
    'Disallow: /admin',
    'Disallow: /favoritos',
    'Disallow: /mensajes',
    'Disallow: /mi-anuncio',
    'Disallow: /activar',
    '',
    `Sitemap: ${base}/sitemap.xml`,
    '',
  ].join('\n');
}
