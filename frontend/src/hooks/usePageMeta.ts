import { useEffect } from 'react';
import { useLocation } from 'react-router';
import {
  DEFAULT_DESCRIPTION,
  canonicalUrl,
  isIndexablePath,
  robotsFor,
  type SeoPageType,
} from '../seo/metadata';

export { DEFAULT_DESCRIPTION, metaDescription } from '../seo/metadata';

export interface PageMeta {
  title: string;
  description?: string;
  image?: string;
  canonical?: string;
  robots?: string;
  type?: SeoPageType;
}

function upsertMeta(attribute: 'name' | 'property', key: string, content?: string) {
  const selector = `meta[${attribute}="${key}"]`;
  const existing = document.head.querySelector<HTMLMetaElement>(selector);
  if (!content) {
    existing?.remove();
    return;
  }
  const element = existing ?? document.createElement('meta');
  if (!existing) {
    element.setAttribute(attribute, key);
    document.head.appendChild(element);
  }
  element.setAttribute('content', content);
}

function upsertLink(rel: string, href?: string) {
  const selector = `link[rel="${rel}"]`;
  const existing = document.head.querySelector<HTMLLinkElement>(selector);
  if (!href) {
    existing?.remove();
    return;
  }
  const element = existing ?? document.createElement('link');
  if (!existing) {
    element.setAttribute('rel', rel);
    document.head.appendChild(element);
  }
  element.setAttribute('href', href);
}

/**
 * Keeps the document metadata in sync with the current page. It is intentionally a hook and
 * not a component so the caller can derive the values from the page's own data.
 *
 * SEO-001: the first response is prerendered by the Vercel function. This hook keeps a client-side
 * navigation (and local `vite dev`) consistent with it: canonical, robots, Open Graph, Twitter Card
 * and the removal of a stale server JSON-LD block.
 */
export function usePageMeta({
  title,
  description = DEFAULT_DESCRIPTION,
  image,
  canonical,
  robots,
  type = 'website',
}: PageMeta) {
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const effectiveRobots = robots ?? robotsFor(location.pathname, searchParams);
  const effectiveCanonical =
    canonical ??
    (isIndexablePath(location.pathname)
      ? canonicalUrl(window.location.origin, location.pathname, searchParams)
      : undefined);

  useEffect(() => {
    document.title = title;
    upsertMeta('name', 'description', description);
    upsertMeta('property', 'og:type', type);
    upsertMeta('property', 'og:title', title);
    upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:url', effectiveCanonical ?? window.location.href);
    upsertMeta('property', 'og:site_name', 'ClaseYa');
    upsertMeta('property', 'og:locale', 'es_AR');
    upsertMeta('property', 'og:image', image);
    upsertMeta('name', 'twitter:card', image ? 'summary_large_image' : 'summary');
    upsertMeta('name', 'twitter:title', title);
    upsertMeta('name', 'twitter:description', description);
    upsertMeta('name', 'twitter:image', image);
    upsertMeta('name', 'robots', effectiveRobots);
    upsertLink('canonical', effectiveCanonical);

    // The prerenderer owns JSON-LD. Remove only a block that belongs to a different URL so a
    // client-side navigation never leaves structured data for the previous profile in the DOM.
    const currentPath = `${location.pathname}${location.search}`;
    document
      .querySelectorAll<HTMLScriptElement>('script[data-seo-jsonld]')
      .forEach((script) => {
        if (script.dataset.seoPath !== currentPath) script.remove();
      });
  }, [
    title,
    description,
    image,
    canonical,
    effectiveCanonical,
    effectiveRobots,
    type,
    location.pathname,
    location.search,
  ]);
}
