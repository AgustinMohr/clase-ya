import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * SEO-001: self-contained Vercel function. It bundles the server renderer and the handler on
 * purpose: Vercel transpiles this file without bundling imports outside the function directory,
 * so importing `src/seo/metadata` here produced ERR_MODULE_NOT_FOUND at runtime.
 *
 * The client keeps its own small helpers in `src/seo/metadata.ts`; this file is the server twin.
 */

export const DEFAULT_DESCRIPTION =
  'ClaseYa — encontrá profesores particulares por materia, modalidad y zona. Contactalos gratis.';

export const SITE_NAME = 'ClaseYa';

export interface SeoSubject {
  id: string;
  name: string;
}

export interface SeoTeacherSubject {
  subjectName?: string;
  careerName?: string;
  universityName?: string;
}

export interface SeoTeacherEducation {
  institution?: string;
  degree?: string;
  startYear?: number;
  endYear?: number;
  isVerified?: boolean;
}

export interface SeoTeacher {
  id: string;
  displayName?: string;
  bio?: string;
  ratingAverage?: number;
  ratingCount?: number;
  verificationStatus?: string;
  pricePerHour?: number;
  city?: string;
  photoUrl?: string;
  availabilityNote?: string;
  modalities?: string[];
  subjects?: SeoTeacherSubject[];
  education?: SeoTeacherEducation[];
}

export type SeoPageType = 'website' | 'profile';

export interface SeoDocument {
  /** Original pathname + query, used to identify the server JSON-LD on client navigation. */
  path: string;
  title: string;
  description: string;
  canonical: string;
  robots?: string;
  type: SeoPageType;
  image?: string;
  jsonLd?: Record<string, unknown>;
  bodyHtml: string;
}

/** Collapses whitespace and clips a public text so it can be used as a meta description. */
export function metaDescription(text: string | null | undefined, max = 160): string | undefined {
  if (!text) return undefined;
  const clean = text.replace(/\s+/g, ' ').trim();
  if (!clean) return undefined;
  return clean.length > max ? `${clean.slice(0, max - 1).trimEnd()}…` : clean;
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
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

function teacherName(teacher: SeoTeacher): string {
  const name = teacher.displayName?.trim();
  return name || 'Profesor';
}

function subjectNames(teacher: SeoTeacher): string[] {
  return (teacher.subjects ?? [])
    .map((subject) => subject.subjectName?.trim())
    .filter((name): name is string => Boolean(name));
}

function modalityLabel(value: string): string {
  if (value === 'ONLINE') return 'Online';
  if (value === 'IN_PERSON') return 'Presencial';
  return value;
}

function formatPrice(value: number): string {
  return `$${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 }).format(value)}`;
}

function ratingCount(teacher: SeoTeacher): number {
  return Number(teacher.ratingCount ?? 0);
}

function ratingAverage(teacher: SeoTeacher): number | undefined {
  if (teacher.ratingAverage == null) return undefined;
  const value = Number(teacher.ratingAverage);
  return Number.isFinite(value) ? value : undefined;
}

function imageUrl(value: string | undefined, origin: string): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value, normalizeOrigin(origin));
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.toString() : undefined;
  } catch {
    return undefined;
  }
}

function teacherJsonLd(
  teacher: SeoTeacher,
  canonical: string,
  image: string | undefined,
): Record<string, unknown> {
  const name = teacherName(teacher);
  const personId = `${canonical}#person`;
  const serviceId = `${canonical}#service`;
  const subjects = subjectNames(teacher);
  const city = teacher.city?.trim();

  const person: Record<string, unknown> = {
    '@type': 'Person',
    '@id': personId,
    name,
    url: canonical,
    jobTitle: 'Profesor particular',
  };
  if (teacher.bio?.trim()) person.description = teacher.bio.trim();
  if (image) person.image = image;
  if (city) person.address = { '@type': 'PostalAddress', addressLocality: city };
  if (subjects.length) person.knowsAbout = subjects;
  if (teacher.education?.length) {
    person.alumniOf = teacher.education
      .filter((education) => education.institution?.trim())
      .map((education) => ({
        '@type': 'EducationalOrganization',
        name: education.institution!.trim(),
      }));
  }

  const service: Record<string, unknown> = {
    '@type': 'Service',
    '@id': serviceId,
    name: subjects.length ? `Clases de ${subjects.slice(0, 3).join(', ')}` : 'Clases particulares',
    serviceType: 'Clases particulares',
    provider: { '@id': personId },
    url: canonical,
  };
  if (city) service.areaServed = city;
  if (teacher.pricePerHour != null) {
    service.offers = {
      '@type': 'Offer',
      price: String(teacher.pricePerHour),
      priceCurrency: 'ARS',
      url: canonical,
    };
  }
  const count = ratingCount(teacher);
  const average = ratingAverage(teacher);
  if (count > 0 && average != null) {
    service.aggregateRating = {
      '@type': 'AggregateRating',
      ratingValue: average,
      reviewCount: count,
      bestRating: 5,
      worstRating: 1,
    };
  }

  return {
    '@context': 'https://schema.org',
    '@graph': [
      person,
      service,
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Inicio', item: `${new URL(canonical).origin}/` },
          { '@type': 'ListItem', position: 2, name, item: canonical },
        ],
      },
    ],
  };
}

function teacherBody(teacher: SeoTeacher, canonical: string): string {
  const name = teacherName(teacher);
  const subjects = subjectNames(teacher);
  const modalities = (teacher.modalities ?? []).filter(Boolean);
  const count = ratingCount(teacher);
  const average = ratingAverage(teacher);
  const parts: string[] = ['<main><article>', `<h1>${escapeHtml(name)}</h1>`];

  if (teacher.city?.trim()) parts.push(`<p>${escapeHtml(teacher.city.trim())}</p>`);
  if (count > 0 && average != null) {
    parts.push(`<p>${average.toFixed(1)} de 5 (${count} opiniones)</p>`);
  }
  if (teacher.bio?.trim()) parts.push(`<p>${escapeHtml(teacher.bio.trim())}</p>`);
  if (teacher.pricePerHour != null) {
    parts.push(`<p>Precio: ${escapeHtml(formatPrice(Number(teacher.pricePerHour)))} por hora</p>`);
  }
  if (modalities.length) {
    parts.push('<h2>Modalidades</h2><ul>');
    for (const modality of modalities) parts.push(`<li>${escapeHtml(modalityLabel(modality))}</li>`);
    parts.push('</ul>');
  }
  if (subjects.length) {
    parts.push('<h2>Materias</h2><ul>');
    for (const subject of subjects) parts.push(`<li>${escapeHtml(subject)}</li>`);
    parts.push('</ul>');
  }
  if (teacher.availabilityNote?.trim()) {
    parts.push('<h2>Disponibilidad</h2>');
    parts.push(`<p>${escapeHtml(teacher.availabilityNote.trim())}</p>`);
  }
  if (teacher.education?.length) {
    parts.push('<h2>Formación</h2><ul>');
    for (const education of teacher.education) {
      const degree = education.degree?.trim() ?? '';
      const institution = education.institution?.trim() ?? '';
      const years =
        education.startYear != null
          ? ` (${education.startYear}${education.endYear ? `–${education.endYear}` : ''})`
          : '';
      const label = [degree, institution].filter(Boolean).join(' — ');
      if (label) parts.push(`<li>${escapeHtml(`${label}${years}`)}</li>`);
    }
    parts.push('</ul>');
  }
  parts.push(`<p><a href="${escapeHtml(canonical)}">Ver perfil completo en ${SITE_NAME}</a></p>`);
  parts.push(
    '<noscript><p>Activá JavaScript para contactar al profesor y guardar favoritos.</p></noscript>',
  );
  parts.push('</article></main>');
  return parts.join('');
}

export function buildTeacherSeo(teacher: SeoTeacher, origin: string): SeoDocument {
  const canonical = canonicalUrl(origin, `/profesores/${encodeURIComponent(teacher.id)}`);
  const name = teacherName(teacher);
  const city = teacher.city?.trim();
  const title = city ? `${name} — clases en ${city} | ${SITE_NAME}` : `${name} | ${SITE_NAME}`;
  const description =
    metaDescription(teacher.bio) ??
    `Mirá la disponibilidad, materias y precio por hora de ${name} en ${SITE_NAME}.`;
  const image = imageUrl(teacher.photoUrl, origin);

  return {
    path: new URL(canonical).pathname,
    title,
    description,
    canonical,
    type: 'profile',
    image,
    jsonLd: teacherJsonLd(teacher, canonical, image),
    bodyHtml: teacherBody(teacher, canonical),
  };
}

export function buildSearchSeo(
  term: string,
  teachers: SeoTeacher[],
  total: number,
  origin: string,
  indexable: boolean,
): SeoDocument {
  const trimmed = term.trim();
  const canonical = canonicalUrl(origin, '/buscar', new URLSearchParams(trimmed ? { materia: trimmed } : {}));
  const title = trimmed
    ? `Profesores de ${trimmed} | ${SITE_NAME}`
    : `Buscar profesores particulares | ${SITE_NAME}`;
  const description = trimmed
    ? `Encontrá y compará profesores de ${trimmed} por precio, modalidad y disponibilidad. Contactalos gratis.`
    : 'Buscá profesores particulares por materia, modalidad, precio y calificación. Contactalos gratis.';

  const visible = teachers.slice(0, 20);
  const parts: string[] = ['<main>', `<h1>${escapeHtml(title.replace(` | ${SITE_NAME}`, ''))}</h1>`];
  if (indexable) {
    parts.push(`<p>${total} profesor${total === 1 ? '' : 'es'} encontrado${total === 1 ? '' : 's'}</p>`);
    if (visible.length) {
      parts.push('<ul>');
      for (const teacher of visible) {
        const subjectList = subjectNames(teacher).slice(0, 3).join(', ');
        const count = ratingCount(teacher);
        const average = ratingAverage(teacher);
        const details = [
          subjectList,
          average != null && count > 0 ? `${average.toFixed(1)} ★ (${count})` : '',
          teacher.pricePerHour != null ? `${formatPrice(Number(teacher.pricePerHour))}/h` : '',
          teacher.city?.trim() ?? '',
        ].filter(Boolean);
        const profileUrl = canonicalUrl(origin, `/profesores/${encodeURIComponent(teacher.id)}`);
        parts.push(
          `<li><a href="${escapeHtml(profileUrl)}">${escapeHtml(teacherName(teacher))}</a>${details.length ? ` — ${escapeHtml(details.join(' · '))}` : ''}</li>`,
        );
      }
      parts.push('</ul>');
    } else if (!trimmed) {
      parts.push('<p>Buscá profesores por materia, modalidad o zona.</p>');
    } else {
      parts.push(`<p>No encontramos profesores para ${escapeHtml(trimmed)}.</p>`);
    }
  } else {
    parts.push('<p>Los filtros de búsqueda se aplican en la aplicación.</p>');
  }
  parts.push('<noscript><p>Activá JavaScript para buscar y contactar profesores.</p></noscript>');
  parts.push('</main>');

  const jsonLd =
    indexable && visible.length
      ? {
          '@context': 'https://schema.org',
          '@type': 'ItemList',
          itemListElement: visible.map((teacher, index) => ({
            '@type': 'ListItem',
            position: index + 1,
            url: canonicalUrl(origin, `/profesores/${encodeURIComponent(teacher.id)}`),
            name: teacherName(teacher),
          })),
        }
      : undefined;

  return {
    path: new URL(canonical).pathname + new URL(canonical).search,
    title,
    description,
    canonical,
    type: 'website',
    robots: indexable ? undefined : 'noindex,follow',
    jsonLd,
    bodyHtml: parts.join(''),
  };
}

export function buildHomeSeo(subjects: SeoSubject[], origin: string): SeoDocument {
  const base = normalizeOrigin(origin);
  const canonical = `${base}/`;
  const parts: string[] = [
    '<main>',
    `<h1>${SITE_NAME} — Profesores particulares</h1>`,
    '<p>Encontrá profesores por materia, modalidad y zona. Contactalos gratis.</p>',
  ];
  if (subjects.length) {
    parts.push('<h2>Materias populares</h2><ul>');
    for (const subject of subjects.slice(0, 12)) {
      const url = `${base}/buscar?materia=${encodeURIComponent(subject.name)}`;
      parts.push(`<li><a href="${escapeHtml(url)}">${escapeHtml(subject.name)}</a></li>`);
    }
    parts.push('</ul>');
  }
  parts.push('<noscript><p>Activá JavaScript para usar el buscador y contactar profesores.</p></noscript>');
  parts.push('</main>');

  return {
    path: '/',
    title: `${SITE_NAME} — Profesores particulares`,
    description: DEFAULT_DESCRIPTION,
    canonical,
    type: 'website',
    jsonLd: {
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: SITE_NAME,
      url: canonical,
      potentialAction: {
        '@type': 'SearchAction',
        target: `${base}/buscar?materia={search_term_string}`,
        'query-input': 'required name=search_term_string',
      },
    },
    bodyHtml: parts.join(''),
  };
}

export function buildFallbackSeo(origin: string, pathname: string, searchParams?: URLSearchParams): SeoDocument {
  const canonical = canonicalUrl(origin, pathname, searchParams);
  return {
    path: pathname + (searchParams?.toString() ? `?${searchParams.toString()}` : ''),
    title: `${SITE_NAME} — Profesores particulares`,
    description: DEFAULT_DESCRIPTION,
    canonical,
    type: 'website',
    robots: robotsFor(pathname, searchParams),
    bodyHtml: '<main><p>Cargando ClaseYa…</p><noscript><p>Activá JavaScript para usar ClaseYa.</p></noscript></main>',
  };
}

export function buildNotFoundSeo(origin: string, pathname: string): SeoDocument {
  const canonical = canonicalUrl(origin, pathname);
  return {
    path: pathname,
    title: `Perfil no disponible | ${SITE_NAME}`,
    description: 'El perfil que buscás no existe o ya no está disponible en ClaseYa.',
    canonical,
    robots: 'noindex,nofollow',
    type: 'website',
    bodyHtml:
      '<main><h1>Perfil no disponible</h1><p>El perfil que buscás no existe o ya no está disponible.</p><p><a href="/">Volver al inicio</a></p></main>',
  };
}

function jsonLdText(value: Record<string, unknown>): string {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

async function sha256Base64(value: string): Promise<string> {
  const digest = await globalThis.crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(value),
  );
  const bytes = new Uint8Array(digest);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function headTags(seo: SeoDocument): string {
  const tags = [
    `<meta name="description" content="${escapeHtml(seo.description)}">`,
    `<link rel="canonical" href="${escapeHtml(seo.canonical)}">`,
    `<meta property="og:type" content="${seo.type}">`,
    `<meta property="og:title" content="${escapeHtml(seo.title)}">`,
    `<meta property="og:description" content="${escapeHtml(seo.description)}">`,
    `<meta property="og:url" content="${escapeHtml(seo.canonical)}">`,
    `<meta property="og:site_name" content="${SITE_NAME}">`,
    '<meta property="og:locale" content="es_AR">',
    `<meta name="twitter:card" content="${seo.image ? 'summary_large_image' : 'summary'}">`,
    `<meta name="twitter:title" content="${escapeHtml(seo.title)}">`,
    `<meta name="twitter:description" content="${escapeHtml(seo.description)}">`,
  ];
  if (seo.robots) tags.push(`<meta name="robots" content="${escapeHtml(seo.robots)}">`);
  if (seo.image) {
    tags.push(`<meta property="og:image" content="${escapeHtml(seo.image)}">`);
    tags.push(`<meta name="twitter:image" content="${escapeHtml(seo.image)}">`);
  }
  return tags.join('\n    ');
}

function replaceCspHash(html: string, hash: string): string {
  return html.replace(
    /<meta http-equiv="Content-Security-Policy" content="([^"]+)"/i,
    (_match, content: string) => {
      const next = content.replace(
        /script-src ([^;]+)/,
        (_directive, sources: string) => `script-src ${sources} 'sha256-${hash}'`,
      );
      return `<meta http-equiv="Content-Security-Policy" content="${next}"`;
    },
  );
}

/**
 * Injects the head tags, the nonce-free JSON-LD (authorized by a CSP hash) and the server-rendered
 * body into the built index.html. Returns the original HTML untouched when the expected markers are
 * missing, so a template change degrades to the SPA instead of breaking the response.
 */
export async function injectSeo(html: string, seo: SeoDocument): Promise<string> {
  const jsonLd = seo.jsonLd ? jsonLdText(seo.jsonLd) : undefined;
  const hash = jsonLd ? await sha256Base64(jsonLd) : undefined;

  let output = html
    .replace(/<title>[^<]*<\/title>/i, `<title>${escapeHtml(seo.title)}</title>`)
    .replace(/\s*<meta name="description"[^>]*>/gi, '')
    .replace(/\s*<meta name="robots"[^>]*>/gi, '')
    .replace(/\s*<meta property="og:[^"]*"[^>]*>/gi, '')
    .replace(/\s*<meta name="twitter:[^"]*"[^>]*>/gi, '')
    .replace(/\s*<link rel="canonical"[^>]*>/gi, '');

  if (hash) output = replaceCspHash(output, hash);

  const tags = [headTags(seo)];
  if (jsonLd && hash) {
    tags.push(
      `<script type="application/ld+json" data-seo-jsonld data-seo-path="${escapeHtml(seo.path)}">${jsonLd}</script>`,
    );
  }
  output = output.replace('</head>', `    ${tags.join('\n    ')}\n  </head>`);
  output = output.replace(
    /<div id="root">\s*<\/div>/,
    `<div id="root">${seo.bodyHtml}</div>`,
  );
  return output;
}


/** Structural view of the Vercel Node request/response; avoids shipping @vercel/node types. */
interface SeoRequest {
  query: Record<string, string | string[] | undefined>;
  headers: Record<string, string | string[] | undefined>;
}

interface SeoResponse {
  status(code: number): SeoResponse;
  setHeader(name: string, value: string): void;
  send(body: string): void;
}

const FETCH_TIMEOUT_MS = 2_000;
const HOME_CACHE = 'public, s-maxage=3600, stale-while-revalidate=86400';
const SEARCH_CACHE = 'public, s-maxage=300, stale-while-revalidate=3600';
const TEACHER_CACHE = 'public, s-maxage=60, stale-while-revalidate=300';
const NO_STORE = 'no-store';

interface Page<T> {
  content: T[];
  totalElements: number;
}

const SEO_BASE = 'https://seo.local';
let cachedTemplate: string | null = null;

/**
 * SEO-001: server-rendered HTML for the three public routes. The SPA still mounts over #root and
 * takes over; this function only makes the first response useful to crawlers and social previews.
 *
 * It is deliberately fail-open: a backend problem returns the SPA shell with default metadata
 * instead of a 5xx.
 */
export default async function handler(req: SeoRequest, res: SeoResponse): Promise<void> {
  const startedAt = Date.now();
  const rawPath = typeof req.query.path === 'string' ? req.query.path : '';
  const parsed = parseRelativePath(rawPath);
  const origin = publicOrigin(req);
  const bypass = header(req, 'x-vercel-protection-bypass');
  const template = await loadTemplate(origin, bypass);

  const finish = async (seo: SeoDocument, status: number, cacheControl: string, error?: string) => {
    const html = template ? await injectSeo(template, seo) : minimalHtml(seo);
    res.status(status);
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('Cache-Control', cacheControl);
    applySecurityHeaders(res);
    console.log(JSON.stringify({
      event: 'seo_render',
      path: rawPath,
      status,
      template: Boolean(template),
      durationMs: Date.now() - startedAt,
      ...(error ? { error } : {}),
    }));
    res.send(html);
  };

  if (!parsed) {
    await finish(buildNotFoundSeo(origin, '/'), 404, NO_STORE, 'invalid path');
    return;
  }

  const { pathname, searchParams } = parsed;
  const term = (searchParams.get('materia') ?? '').trim();

  try {
    if (pathname === '/') {
      const popular = await fetchJson<Page<SeoSubject>>('/api/subjects/popular?limit=12');
      if (popular.status !== 200 || !popular.body) throw new Error('popular unavailable');
      await finish(buildHomeSeo(popular.body.content, origin), 200, HOME_CACHE);
      return;
    }

    if (pathname === '/buscar') {
      if (!isIndexableSearch(searchParams)) {
        await finish(buildSearchSeo(term, [], 0, origin, false), 200, SEARCH_CACHE);
        return;
      }
      if (!term) {
        await finish(buildSearchSeo('', [], 0, origin, true), 200, SEARCH_CACHE);
        return;
      }

      const subjects = await fetchJson<Page<SeoSubject>>(
        `/api/subjects?query=${encodeURIComponent(term)}&size=10`,
      );
      if (subjects.status !== 200 || !subjects.body) throw new Error('subjects unavailable');
      const match = subjects.body.content.find(
        (subject) => subject.name.trim().toLowerCase() === term.toLowerCase(),
      );
      if (!match) {
        const seo = buildSearchSeo(term, [], 0, origin, true);
        seo.robots = 'noindex,follow';
        await finish(seo, 200, SEARCH_CACHE);
        return;
      }

      const teachers = await fetchJson<Page<SeoTeacher>>(
        `/api/teachers?subjectId=${encodeURIComponent(match.id)}&page=0&size=20`,
      );
      if (teachers.status !== 200 || !teachers.body) throw new Error('teachers unavailable');
      await finish(
        buildSearchSeo(term, teachers.body.content, teachers.body.totalElements, origin, true),
        200,
        SEARCH_CACHE,
      );
      return;
    }

    const teacherMatch = pathname.match(/^\/profesores\/([0-9a-fA-F-]{36})$/);
    if (!teacherMatch) {
      await finish(buildNotFoundSeo(origin, pathname), 404, NO_STORE);
      return;
    }

    const teacher = await fetchJson<SeoTeacher>(`/api/teachers/${teacherMatch[1]}`);
    if (teacher.status === 404) {
      await finish(buildNotFoundSeo(origin, pathname), 404, NO_STORE);
      return;
    }
    if (teacher.status !== 200 || !teacher.body) throw new Error('teacher unavailable');
    await finish(buildTeacherSeo(teacher.body, origin), 200, TEACHER_CACHE);
  } catch (error) {
    const message = error instanceof Error ? error.message : 'unknown error';
    await finish(buildFallbackSeo(origin, pathname, searchParams), 200, NO_STORE, message);
  }
}

function parseRelativePath(raw: string): { pathname: string; searchParams: URLSearchParams } | null {
  if (!raw.startsWith('/') || raw.startsWith('//')) return null;
  try {
    const url = new URL(raw, SEO_BASE);
    if (url.origin !== SEO_BASE) return null;
    const pathname = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, '') : '/';
    return { pathname, searchParams: url.searchParams };
  } catch {
    return null;
  }
}

function publicOrigin(req: SeoRequest): string {
  const configured = (process.env.PUBLIC_SITE_URL ?? process.env.APP_BASE_URL ?? '').trim();
  if (configured) return configured.replace(/\/+$/, '');

  // In production, never trust the Host header for canonical URLs. PUBLIC_SITE_URL (or the
  // backend's APP_BASE_URL) should be set; Vercel's production URL is a trusted fallback.
  if (process.env.VERCEL_ENV === 'production') {
    const production = process.env.VERCEL_PROJECT_PRODUCTION_URL?.trim();
    return production ? `https://${production}` : 'https://claseya.app';
  }

  const proto = header(req, 'x-forwarded-proto') ?? 'https';
  const host = header(req, 'x-forwarded-host') ?? header(req, 'host');
  return host ? `${proto}://${host}` : 'https://claseya.app';
}

function header(req: SeoRequest, name: string): string | undefined {
  const value = req.headers[name];
  return Array.isArray(value) ? value[0] : value;
}

async function fetchJson<T>(path: string): Promise<{ status: number; body: T | null }> {
  const base = (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
  const response = await fetch(`${base}${path}`, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
  });
  if (!response.ok) return { status: response.status, body: null };
  return { status: response.status, body: (await response.json()) as T };
}

async function loadTemplate(origin: string, bypass?: string): Promise<string | null> {
  if (cachedTemplate) return cachedTemplate;

  // process.cwd() differs between local/vercel runtimes; try both the project-root and repo-root
  // layouts. IncludeFiles may place dist/ under either one.
  const candidates = [
    join(process.cwd(), 'dist/index.html'),
    join(process.cwd(), 'frontend/dist/index.html'),
    join(process.cwd(), '..', 'dist/index.html'),
  ];
  for (const candidate of candidates) {
    try {
      const html = readFileSync(candidate, 'utf8');
      if (isSpaShell(html)) {
        cachedTemplate = html;
        return cachedTemplate;
      }
    } catch {
      // Try the next candidate.
    }
  }

  try {
    const headers: Record<string, string> = {};
    if (bypass) headers['x-vercel-protection-bypass'] = bypass;
    const response = await fetch(`${origin}/index.html`, {
      headers,
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
    if (response.ok) {
      const html = await response.text();
      // A protected preview can answer with the Vercel login page. Never cache/inject that.
      if (isSpaShell(html)) {
        cachedTemplate = html;
        return cachedTemplate;
      }
    }
  } catch {
    // Fall through to the minimal document.
  }
  return null;
}

function isSpaShell(html: string): boolean {
  return html.includes('<div id="root">') && html.includes('/assets/');
}

function minimalHtml(seo: SeoDocument): string {
  const robots = seo.robots
    ? `<meta name="robots" content="${escapeHtml(seo.robots)}">`
    : '';
  return [
    '<!doctype html><html lang="es"><head><meta charset="UTF-8">',
    '<meta name="viewport" content="width=device-width, initial-scale=1.0">',
    `<title>${escapeHtml(seo.title)}</title>`,
    `<meta name="description" content="${escapeHtml(seo.description)}">`,
    `<link rel="canonical" href="${escapeHtml(seo.canonical)}">`,
    robots,
    '</head><body>',
    seo.bodyHtml,
    '</body></html>',
  ].join('');
}

function applySecurityHeaders(res: SeoResponse): void {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  res.setHeader('Content-Security-Policy', "frame-ancestors 'none'");
}
