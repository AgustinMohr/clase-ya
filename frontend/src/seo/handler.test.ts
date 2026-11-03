import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import handler from '../../api/seo';

const ORIGIN = 'https://claseya.test';
const BACKEND = 'https://api.claseya.test';
const TEACHER_ID = '11111111-1111-1111-1111-111111111111';

const indexHtml = `<!doctype html><html lang="es"><head><title>ClaseYa</title>
<meta name="description" content="old" />
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self'; object-src 'none';" />
<script type="module" src="/assets/app.js"></script>
</head><body><div id="root"></div></body></html>`;

const teacher = {
  id: TEACHER_ID,
  displayName: 'Ana Pérez',
  bio: 'Profesora de matemática.',
  ratingAverage: 4.8,
  ratingCount: 12,
  pricePerHour: 1500,
  city: 'Santa Fe',
  photoUrl: 'https://cdn.example/ana.jpg',
  availabilityNote: 'Lunes a viernes por la tarde.',
  modalities: ['ONLINE'],
  subjects: [{ subjectName: 'Matemática' }],
  education: [{ institution: 'UNL', degree: 'Licenciada en Matemática' }],
};

interface FakeResponse {
  status: number;
  headers: Record<string, string>;
  body: string;
}

function fakeResponse(): { response: FakeResponse; res: Parameters<typeof handler>[1] } {
  const response: FakeResponse = { status: 0, headers: {}, body: '' };
  const res = {
    status(code: number) {
      response.status = code;
      return res;
    },
    setHeader(name: string, value: string) {
      response.headers[name.toLowerCase()] = value;
    },
    send(body: string) {
      response.body = body;
    },
  };
  return { response, res };
}

function json(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

async function run(path: string) {
  const { response, res } = fakeResponse();
  await handler({ query: { path }, headers: { host: 'preview.vercel.app' } }, res);
  return response;
}

beforeEach(() => {
  process.env.PUBLIC_SITE_URL = ORIGIN;
  process.env.BACKEND_URL = BACKEND;
  delete process.env.VERCEL_ENV;
  vi.spyOn(console, 'log').mockImplementation(() => undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  delete process.env.PUBLIC_SITE_URL;
  delete process.env.BACKEND_URL;
});

describe('Vercel SEO handler', () => {
  it('renders a visible profile with metadata, JSON-LD and a cacheable response', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${BACKEND}/api/teachers/${TEACHER_ID}`) return json(teacher);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    }));

    const response = await run(`/profesores/${TEACHER_ID}`);

    expect(response.status).toBe(200);
    expect(response.headers['cache-control']).toContain('s-maxage=60');
    expect(response.body).toContain('<title>Ana Pérez — clases en Santa Fe | ClaseYa</title>');
    expect(response.body).toContain(`<link rel="canonical" href="${ORIGIN}/profesores/${TEACHER_ID}">`);
    expect(response.body).toContain('<script type="application/ld+json" data-seo-jsonld');
    expect(response.body).toContain('<h1>Ana Pérez</h1>');
    expect(response.body).not.toContain('old');
  });

  it('normalizes a trailing slash before resolving the profile', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${BACKEND}/api/teachers/${TEACHER_ID}`) return json(teacher);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    }));

    const response = await run(`/profesores/${TEACHER_ID}/`);

    expect(response.status).toBe(200);
    expect(response.body).toContain(`<link rel="canonical" href="${ORIGIN}/profesores/${TEACHER_ID}">`);
  });

  it('returns 404 and no profile data for a hidden or missing teacher', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${BACKEND}/api/teachers/${TEACHER_ID}`) return json({}, 404);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    }));

    const response = await run(`/profesores/${TEACHER_ID}`);

    expect(response.status).toBe(404);
    expect(response.headers['cache-control']).toBe('no-store');
    expect(response.body).toContain('Perfil no disponible');
    expect(response.body).toContain('<meta name="robots" content="noindex,nofollow">');
    expect(response.body).not.toContain('Ana Pérez');
  });

  it('renders a clean search with an ItemList and canonical page 1', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.startsWith(`${BACKEND}/api/subjects?query=Matem`)) {
        return json({ content: [{ id: 'subject-1', name: 'Matemática' }], totalElements: 1 });
      }
      if (url.startsWith(`${BACKEND}/api/teachers?subjectId=subject-1`)) {
        return json({ content: [teacher], totalElements: 1 });
      }
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    }));

    const response = await run('/buscar?materia=Matem%C3%A1tica');

    expect(response.status).toBe(200);
    expect(response.headers['cache-control']).toContain('s-maxage=300');
    expect(response.body).toContain(
      `<link rel="canonical" href="${ORIGIN}/buscar?materia=Matem%C3%A1tica">`,
    );
    expect(response.body).toContain('"@type":"ItemList"');
    expect(response.body).toContain('Ana Pérez');
  });

  it('marks a filtered search noindex and does not query teacher results', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const response = await run('/buscar?materia=mate&modalidad=ONLINE');

    expect(response.status).toBe(200);
    expect(response.body).toContain('<meta name="robots" content="noindex,follow">');
    expect(response.body).toContain(
      `<link rel="canonical" href="${ORIGIN}/buscar?materia=mate">`,
    );
    expect(response.body).not.toContain('ItemList');
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining('/api/teachers?subjectId='),
      expect.anything(),
    );
  });

  it('fails open with the SPA shell when the backend is unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error('backend down');
    }));

    const response = await run(`/profesores/${TEACHER_ID}`);

    expect(response.status).toBe(200);
    expect(response.headers['cache-control']).toBe('no-store');
    expect(response.body).toContain('<title>ClaseYa — Profesores particulares</title>');
  });

  it('rejects invalid absolute-path input without fetching the backend', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url === `${ORIGIN}/index.html`) return json(indexHtml);
      throw new Error(`unexpected fetch ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    const response = await run('//evil.example/profesores/abc');

    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalledWith(
      expect.stringContaining('/api/'),
      expect.anything(),
    );
  });
});
