import { useEffect } from 'react';
import { useLocation } from 'react-router';

export const DEFAULT_DESCRIPTION =
  'ClaseYa — encontrá profesores particulares por materia, modalidad y zona. Contactalos gratis.';

export interface PageMeta {
  title: string;
  description?: string;
  image?: string;
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

/**
 * Keeps the document metadata in sync with the current page. It is intentionally a hook and
 * not a component so the caller can derive the values from the page's own data.
 *
 * The tags are injected on the client: Google renders JS, so it sees them, but social crawlers
 * (WhatsApp, Facebook, X) do not. Previews from those clients require server rendering (see
 * ARCH-001, out of scope).
 */
export function usePageMeta({ title, description = DEFAULT_DESCRIPTION, image }: PageMeta) {
  const location = useLocation();

  useEffect(() => {
    document.title = title;
    upsertMeta('name', 'description', description);
    upsertMeta('property', 'og:title', title);
    upsertMeta('property', 'og:description', description);
    upsertMeta('property', 'og:type', 'website');
    upsertMeta('property', 'og:url', window.location.href);
    upsertMeta('property', 'og:image', image);
  }, [title, description, image, location.pathname, location.search]);
}

/** Collapses whitespace and clips a public text so it can be used as a meta description. */
export function metaDescription(text: string | null | undefined, max = 160): string | undefined {
  if (!text) return undefined;
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return undefined;
  return clean.length > max ? `${clean.slice(0, max - 1).trimEnd()}…` : clean;
}
