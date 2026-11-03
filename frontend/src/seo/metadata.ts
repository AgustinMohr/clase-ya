/**
 * SEO-001: client-side metadata helpers shared by `usePageMeta` and the public pages.
 *
 * The server twin (HTML builder, JSON-LD and the Vercel handler) lives in `frontend/api/seo.ts`.
 * It is intentionally self-contained because Vercel does not bundle imports outside the function
 * directory. Keep the canonical/indexability rules in sync; both sides are covered by tests.
 */

export const DEFAULT_DESCRIPTION =
  'ClaseYa — encontrá profesores particulares por materia, modalidad y zona. Contactalos gratis.';

export type SeoPageType = 'website' | 'profile';

/** Collapses whitespace and clips a public text so it can be used as a meta description. */
export function metaDescription(text: string | null | undefined, max = 160): string | undefined {
  if (!text) return undefined;
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return undefined;
  return clean.length > max ? `${clean.slice(0, max - 1).trimEnd()}…` : clean;
}

function normalizeOrigin(origin: string): string {
  return origin.replace(/\/+$/, '');
}

function normalizePath(pathname: string): string {
  if (!pathname) return '/';
  const withSlash = pathname.startsWith('/') ? pathname : `/${pathname}`;
  if (withSlash === '/') return '/';
  return withSlash.replace(/\/+$/, '');
}

/** Absolute canonical URL: drops tracking params and keeps only a normalized `materia` term. */
export function canonicalUrl(
  origin: string,
  pathname: string,
  searchParams?: URLSearchParams,
): string {
  const base = normalizeOrigin(origin);
  const path = normalizePath(pathname);
  if (path === '/buscar') {
    const term = (searchParams?.get('materia') ?? '').trim();
    return term ? `${base}/buscar?materia=${encodeURIComponent(term)}` : `${base}/buscar`;
  }
  return `${base}${path === '/' ? '/' : path}`;
}

/** Only the landing, the clean search and profile pages are indexable. */
export function isIndexablePath(pathname: string): boolean {
  return pathname === '/' || pathname === '/buscar' || /^\/profesores\/[^/]+$/.test(pathname);
}

/**
 * SEO-001 RF-6: only `materia` and the first page are indexable. Any filter combination is
 * crawlable but not indexable, and canonicalizes to the clean page-1 URL.
 */
export function isIndexableSearch(searchParams: URLSearchParams): boolean {
  for (const [key, value] of searchParams) {
    if (key === 'materia') continue;
    if (key === 'pagina' && value === '1') continue;
    return false;
  }
  return true;
}

/** Robots directive for a path. Public indexable pages return undefined (default index,follow). */
export function robotsFor(pathname: string, searchParams?: URLSearchParams): string | undefined {
  if (!isIndexablePath(pathname)) return 'noindex,nofollow';
  if (pathname === '/buscar' && searchParams && !isIndexableSearch(searchParams)) {
    return 'noindex,follow';
  }
  return undefined;
}
