import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import { usePageMeta, type PageMeta } from './usePageMeta';

function Meta({ meta }: { meta: PageMeta }) {
  usePageMeta(meta);
  return null;
}

function renderAt(path: string, meta: PageMeta) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Meta meta={meta} />
    </MemoryRouter>,
  );
}

function canonical(): HTMLLinkElement | null {
  return document.head.querySelector('link[rel="canonical"]');
}

function meta(name: string): string | null {
  return document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.content ?? null;
}

beforeEach(() => {
  document.head.innerHTML = '';
  document.title = '';
});

describe('usePageMeta', () => {
  it('sets canonical, Open Graph and Twitter tags for a public profile', () => {
    renderAt('/profesores/abc', {
      title: 'Ana | ClaseYa',
      description: 'Perfil de Ana',
      image: 'https://cdn.example/ana.jpg',
      type: 'profile',
    });

    expect(document.title).toBe('Ana | ClaseYa');
    expect(canonical()?.getAttribute('href')).toBe('http://localhost:3000/profesores/abc');
    expect(meta('robots')).toBeNull();
    expect(meta('description')).toBe('Perfil de Ana');
    expect(document.head.querySelector('meta[property="og:type"]')?.getAttribute('content')).toBe('profile');
    expect(meta('twitter:card')).toBe('summary_large_image');
    expect(document.head.querySelector('meta[property="og:image"]')?.getAttribute('content')).toBe(
      'https://cdn.example/ana.jpg',
    );
  });

  it('marks private routes noindex,nofollow and removes any stale canonical', () => {
    const stale = document.createElement('link');
    stale.setAttribute('rel', 'canonical');
    stale.setAttribute('href', 'https://old.example/private');
    document.head.appendChild(stale);

    renderAt('/mensajes/123', { title: 'Mensajes | ClaseYa' });

    expect(meta('robots')).toBe('noindex,nofollow');
    expect(canonical()).toBeNull();
  });

  it('canonicalizes a filtered search to the clean first page and marks it noindex,follow', () => {
    renderAt('/buscar?materia=mate&modalidad=ONLINE&pagina=2', {
      title: 'Profesores de mate | ClaseYa',
    });

    expect(canonical()?.getAttribute('href')).toBe('http://localhost:3000/buscar?materia=mate');
    expect(meta('robots')).toBe('noindex,follow');
  });

  it('keeps the server JSON-LD that belongs to the current URL', () => {
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.dataset.seoJsonld = '';
    script.dataset.seoPath = '/profesores/abc';
    document.head.appendChild(script);

    renderAt('/profesores/abc', { title: 'Ana | ClaseYa' });

    expect(document.head.querySelector('script[data-seo-jsonld]')).not.toBeNull();
  });

  it('removes a server JSON-LD block from a previous URL after client navigation', () => {
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.dataset.seoJsonld = '';
    script.dataset.seoPath = '/profesores/old';
    document.head.appendChild(script);

    renderAt('/profesores/new', { title: 'Nuevo | ClaseYa' });

    expect(document.head.querySelector('script[data-seo-jsonld]')).toBeNull();
  });

  it('omits the image tags when there is no image', () => {
    renderAt('/', { title: 'ClaseYa' });

    expect(meta('twitter:card')).toBe('summary');
    expect(document.head.querySelector('meta[property="og:image"]')).toBeNull();
    expect(document.head.querySelector('meta[name="twitter:image"]')).toBeNull();
  });
});
