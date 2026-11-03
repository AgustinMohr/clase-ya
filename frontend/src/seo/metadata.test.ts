import { describe, expect, it } from 'vitest';
import {
  buildFallbackSeo,
  buildHomeSeo,
  buildNotFoundSeo,
  buildSearchSeo,
  buildTeacherSeo,
  injectSeo,
  type SeoTeacher,
} from '../../api/seo';
import {
  DEFAULT_DESCRIPTION,
  canonicalUrl,
  isIndexableSearch,
  metaDescription,
  robotsFor,
} from './metadata';

const ORIGIN = 'https://claseya.test';

const teacher: SeoTeacher = {
  id: '11111111-1111-1111-1111-111111111111',
  displayName: 'Ana Pérez',
  bio: 'Profesora de matemática con diez años de experiencia.',
  ratingAverage: 4.8,
  ratingCount: 12,
  pricePerHour: 1500,
  city: 'Santa Fe',
  photoUrl: 'https://cdn.example/ana.jpg',
  availabilityNote: 'Lunes a viernes por la tarde.',
  modalities: ['ONLINE', 'IN_PERSON'],
  subjects: [{ subjectName: 'Matemática' }, { subjectName: 'Álgebra' }],
  education: [{ institution: 'UNL', degree: 'Licenciada en Matemática', startYear: 2005, endYear: 2010 }],
};

const indexHtml = `<!doctype html>
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <title>ClaseYa — Profesores particulares</title>
    <meta name="description" content="old description" />
    <meta property="og:title" content="old title" />
    <meta property="og:image" content="https://old.example/image.png" />
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' https://accounts.google.com; object-src 'none';" />
    <script src="/assets/app.js"></script>
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>`;

describe('canonicalUrl', () => {
  it('normalizes the search URL and drops tracking and filter params', () => {
    const params = new URLSearchParams({
      materia: '  Matemática ',
      utm_source: 'google',
      modalidad: 'ONLINE',
    });

    expect(canonicalUrl(ORIGIN, '/buscar', params)).toBe(
      'https://claseya.test/buscar?materia=Matem%C3%A1tica',
    );
  });

  it('keeps a clean profile URL and removes trailing slashes', () => {
    expect(canonicalUrl(`${ORIGIN}/`, '/profesores/abc/')).toBe(
      'https://claseya.test/profesores/abc',
    );
    expect(canonicalUrl(ORIGIN, '/')).toBe('https://claseya.test/');
  });
});

describe('search indexability', () => {
  it('indexes only the first clean search page', () => {
    expect(isIndexableSearch(new URLSearchParams({ materia: 'mate' }))).toBe(true);
    expect(isIndexableSearch(new URLSearchParams({ materia: 'mate', pagina: '1' }))).toBe(true);
    expect(isIndexableSearch(new URLSearchParams({ materia: 'mate', pagina: '2' }))).toBe(false);
    expect(isIndexableSearch(new URLSearchParams({ materia: 'mate', modalidad: 'ONLINE' }))).toBe(false);
    expect(isIndexableSearch(new URLSearchParams({ verificados: 'true' }))).toBe(false);
  });

  it('marks filtered searches noindex,follow and private routes noindex,nofollow', () => {
    expect(robotsFor('/')).toBeUndefined();
    expect(robotsFor('/profesores/abc')).toBeUndefined();
    expect(robotsFor('/buscar', new URLSearchParams({ materia: 'mate' }))).toBeUndefined();
    expect(robotsFor('/buscar', new URLSearchParams({ materia: 'mate', modalidad: 'ONLINE' }))).toBe(
      'noindex,follow',
    );
    expect(robotsFor('/mensajes')).toBe('noindex,nofollow');
    expect(robotsFor('/admin/usuarios')).toBe('noindex,nofollow');
  });
});

describe('buildTeacherSeo', () => {
  it('builds canonical, title, description and structured data', () => {
    const seo = buildTeacherSeo(teacher, ORIGIN);

    expect(seo.title).toBe('Ana Pérez — clases en Santa Fe | ClaseYa');
    expect(seo.description).toBe('Profesora de matemática con diez años de experiencia.');
    expect(seo.canonical).toBe(
      'https://claseya.test/profesores/11111111-1111-1111-1111-111111111111',
    );
    expect(seo.type).toBe('profile');
    expect(seo.image).toBe('https://cdn.example/ana.jpg');

    const serialized = JSON.stringify(seo.jsonLd);
    expect(serialized).toContain('"@type":"Person"');
    expect(serialized).toContain('"@type":"Service"');
    expect(serialized).toContain('"@type":"AggregateRating"');
    expect(serialized).toContain('"ratingValue":4.8');
    expect(serialized).toContain('"reviewCount":12');
    expect(serialized).toContain('"price":"1500"');
    expect(seo.bodyHtml).toContain('<h1>Ana Pérez</h1>');
    expect(seo.bodyHtml).toContain('Matemática');
    expect(seo.bodyHtml).toContain('Online');
    expect(seo.bodyHtml).toContain('$1.500');
  });

  it('omits AggregateRating when there are no reviews', () => {
    const seo = buildTeacherSeo({ ...teacher, ratingAverage: 0, ratingCount: 0 }, ORIGIN);
    expect(JSON.stringify(seo.jsonLd)).not.toContain('AggregateRating');
  });

  it('never renders private fields and escapes user text', () => {
    const privateTeacher = {
      ...teacher,
      displayName: '<script>alert(1)</script>',
      bio: 'Bio </script><script>alert(2)</script>',
      email: 'ana@example.com',
      address: 'Calle privada 123',
      latitude: -31.6333,
      longitude: -60.7,
    } as SeoTeacher;

    const seo = buildTeacherSeo(privateTeacher, ORIGIN);
    const serialized = JSON.stringify(seo);

    expect(seo.bodyHtml).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(serialized).not.toContain('ana@example.com');
    expect(serialized).not.toContain('Calle privada');
    expect(serialized).not.toContain('-31.6333');
    expect(serialized).not.toContain('-60.7');
  });

  it('ignores unsafe image protocols', () => {
    const seo = buildTeacherSeo({ ...teacher, photoUrl: 'javascript:alert(1)' }, ORIGIN);
    expect(seo.image).toBeUndefined();
    expect(JSON.stringify(seo.jsonLd)).not.toContain('javascript:');
  });
});

describe('buildSearchSeo', () => {
  it('builds an ItemList for an indexable search', () => {
    const seo = buildSearchSeo('matemática', [teacher], 1, ORIGIN, true);

    expect(seo.canonical).toBe('https://claseya.test/buscar?materia=matem%C3%A1tica');
    expect(seo.robots).toBeUndefined();
    expect(JSON.stringify(seo.jsonLd)).toContain('"@type":"ItemList"');
    expect(seo.bodyHtml).toContain('Ana Pérez');
    expect(seo.bodyHtml).toContain('1 profesor encontrado');
  });

  it('marks filtered searches noindex and does not invent results', () => {
    const seo = buildSearchSeo('matemática', [], 0, ORIGIN, false);

    expect(seo.robots).toBe('noindex,follow');
    expect(seo.jsonLd).toBeUndefined();
    expect(seo.bodyHtml).toContain('Los filtros de búsqueda se aplican en la aplicación.');
    expect(seo.bodyHtml).not.toContain('No encontramos');
  });

  it('shows an explicit empty state for an unmatched term', () => {
    const seo = buildSearchSeo('xyz', [], 0, ORIGIN, true);
    expect(seo.bodyHtml).toContain('No encontramos profesores');
  });
});

describe('buildHomeSeo', () => {
  it('includes WebSite, SearchAction and popular subject links', () => {
    const seo = buildHomeSeo(
      [
        { id: '1', name: 'Matemática' },
        { id: '2', name: 'Física' },
      ],
      ORIGIN,
    );

    expect(seo.canonical).toBe('https://claseya.test/');
    expect(JSON.stringify(seo.jsonLd)).toContain('"@type":"WebSite"');
    expect(JSON.stringify(seo.jsonLd)).toContain('SearchAction');
    expect(seo.bodyHtml).toContain('/buscar?materia=Matem%C3%A1tica');
    expect(seo.bodyHtml).toContain('/buscar?materia=F%C3%ADsica');
  });
});

describe('buildFallbackSeo and buildNotFoundSeo', () => {
  it('keeps the requested canonical on a backend failure', () => {
    const seo = buildFallbackSeo(ORIGIN, '/profesores/abc');
    expect(seo.canonical).toBe('https://claseya.test/profesores/abc');
    expect(seo.title).toContain('ClaseYa');
  });

  it('marks a missing profile as noindex', () => {
    const seo = buildNotFoundSeo(ORIGIN, '/profesores/abc');
    expect(seo.robots).toBe('noindex,nofollow');
    expect(seo.bodyHtml).toContain('Perfil no disponible');
  });
});

describe('injectSeo', () => {
  it('replaces title/meta tags, adds canonical and renders the server body', async () => {
    const seo = buildTeacherSeo(teacher, ORIGIN);
    const html = await injectSeo(indexHtml, seo);

    expect(html).toContain('<title>Ana Pérez — clases en Santa Fe | ClaseYa</title>');
    expect(html).toContain('<meta name="description" content="Profesora de matemática con diez años de experiencia.">');
    expect(html).toContain('<link rel="canonical" href="https://claseya.test/profesores/11111111-1111-1111-1111-111111111111">');
    expect(html).toContain('<meta property="og:type" content="profile">');
    expect(html).toContain('<meta name="twitter:card" content="summary_large_image">');
    expect(html).toContain('<script type="application/ld+json" data-seo-jsonld');
    expect(html).toContain('<h1>Ana Pérez</h1>');
    expect(html).not.toContain('old description');
    expect(html).not.toContain('old title');
    expect(html).not.toContain('https://old.example/image.png');
  });

  it('authorizes the JSON-LD with a sha256 CSP hash', async () => {
    const seo = buildTeacherSeo(teacher, ORIGIN);
    const html = await injectSeo(indexHtml, seo);

    const csp = html.match(/<meta http-equiv="Content-Security-Policy" content="([^"]+)"/)?.[1] ?? '';
    const jsonLd = html.match(/<script type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/)?.[1] ?? '';
    const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(jsonLd));
    const hash = btoa(String.fromCharCode(...new Uint8Array(digest)));

    expect(csp).toContain(`'sha256-${hash}'`);
    expect(csp).not.toContain("'unsafe-inline'");
  });

  it('escapes a closing script tag inside the JSON-LD payload', async () => {
    const seo = buildTeacherSeo({ ...teacher, bio: 'Cierra </script><script>alert(1)</script>' }, ORIGIN);
    const html = await injectSeo(indexHtml, seo);

    const jsonLd = html.match(/<script type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/)?.[1] ?? '';
    expect(jsonLd).not.toContain('</script>');
    expect(jsonLd).toContain('\\u003c/script');
  });

  it('leaves the template usable when the expected markers are missing', async () => {
    const generic = '<html><head><title>Old</title></head><body><p>no root</p></body></html>';
    const html = await injectSeo(generic, buildNotFoundSeo(ORIGIN, '/nope'));
    expect(html).toContain('<title>Perfil no disponible | ClaseYa</title>');
    expect(html).toContain('no root');
  });

  it('exposes the default description for the SPA hook', () => {
    expect(DEFAULT_DESCRIPTION).toContain('ClaseYa');
    expect(metaDescription('  hola   mundo  ')).toBe('hola mundo');
  });
});
