import { rewrite } from '@vercel/functions';

/**
 * SEO-001: only the public, indexable routes go through the prerender function. Everything else
 * (SPA navigation, private routes, assets) keeps the static deployment untouched.
 */
export const config = {
  matcher: ['/', '/buscar', '/buscar/', '/profesores/:path*'],
};

export default function middleware(request: Request): Response {
  const url = new URL(request.url);
  const target = new URL('/api/seo', url.origin);
  target.searchParams.set('path', `${url.pathname}${url.search}`);
  return rewrite(target);
}
