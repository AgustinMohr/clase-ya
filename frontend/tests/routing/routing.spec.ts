import { expect, test } from '@playwright/test';
import {
  API_URL,
  TEACHER,
  ensureConversationWith,
  firstPublicTeacher,
  loginAs,
  publicTeachers,
  uniqueText,
} from '../helpers';
import { RoutingPage } from './routing-page';

/**
 * ARCH-001: URLs reales, deep links, historial y metadatos. Each test enters the app through a
 * URL (not through the home page) so a broken router cannot hide behind in-app navigation.
 */
test.describe('Routing y URLs compartibles', () => {
  test(
    'un perfil abierto por URL directa sobrevive al refresh y publica metadatos',
    { tag: ['@critical', '@e2e', '@routing', '@ARCH-E2E-001'] },
    async ({ page }) => {
      const teacher = await firstPublicTeacher();

      await page.goto(`/profesores/${teacher.id}`);
      await expect(page.getByRole('heading', { level: 1 })).toContainText(teacher.displayName);
      await expect.poll(async () => page.title()).toContain(teacher.displayName);
      await expect(page.locator('meta[property="og:url"]')).toHaveAttribute(
        'content',
        new RegExp(`/profesores/${teacher.id}$`),
      );

      await page.reload();
      await expect(page.getByRole('heading', { level: 1 })).toContainText(teacher.displayName);
      expect(new URL(page.url()).pathname).toBe(`/profesores/${teacher.id}`);
    },
  );

  test(
    'un profesor no visible por URL directa no filtra datos',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-002'] },
    async ({ page }) => {
      await page.goto('/profesores/00000000-0000-0000-0000-000000000000');
      await expect(page.getByRole('heading', { name: 'Perfil no disponible' })).toBeVisible();
      expect(new URL(page.url()).pathname).toBe('/profesores/00000000-0000-0000-0000-000000000000');
    },
  );

  test(
    'los parámetros inválidos de búsqueda se ignoran sin romper la pantalla',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-003'] },
    async ({ page }) => {
      const routing = new RoutingPage(page);
      await routing.openSearch('?rating=abc&pagina=-3&modalidad=FOO&precioMin=nope&verificados=maybe');

      await expect(routing.cards.first()).toBeVisible();
      await expect(page.getByText(/No pudimos buscar/)).toHaveCount(0);
      await expect(routing.onlineChip).toHaveAttribute('aria-pressed', 'false');
    },
  );

  test(
    'la búsqueda vive en la URL y el filtro sobrevive al refresh y al volver atrás',
    { tag: ['@critical', '@e2e', '@routing', '@ARCH-E2E-004'] },
    async ({ page, request }) => {
      const listing = await request.get(`${API_URL}/api/teachers?page=0&size=1`);
      const body = (await listing.json()) as { content: Array<{ subjects: Array<{ subjectName: string }> }> };
      const subjectName = body.content[0].subjects[0].subjectName;

      const routing = new RoutingPage(page);
      await routing.openSearch(`?materia=${encodeURIComponent(subjectName)}`);
      await expect(routing.term).toHaveValue(subjectName);
      await expect(routing.cards.first()).toBeVisible();
      const baseUrl = page.url();

      await routing.onlineChip.click();
      await routing.applyButton.click();
      await expect(page).toHaveURL(/modalidad=ONLINE/);
      await expect(routing.onlineChip).toHaveAttribute('aria-pressed', 'true');

      await page.reload();
      await expect(routing.onlineChip).toHaveAttribute('aria-pressed', 'true');
      await expect(routing.term).toHaveValue(subjectName);

      await page.goBack();
      await expect(page).toHaveURL(baseUrl);
      await expect(routing.onlineChip).toHaveAttribute('aria-pressed', 'false');
    },
  );

  test(
    'volver desde un perfil restaura la búsqueda con sus filtros',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-005'] },
    async ({ page }) => {
      const routing = new RoutingPage(page);
      await routing.openSearch('?modalidad=ONLINE');
      await expect(routing.cards.first()).toBeVisible();

      await routing.cards.first().click();
      await expect(page).toHaveURL(/\/profesores\//);

      await page.goBack();
      await expect(page).toHaveURL(/\/buscar\?modalidad=ONLINE$/);
      await expect(routing.onlineChip).toHaveAttribute('aria-pressed', 'true');
    },
  );

  test(
    'una ruta privada sin sesión conserva la URL y ofrece el login',
    { tag: ['@critical', '@e2e', '@routing', '@ARCH-E2E-006'] },
    async ({ page }) => {
      const routing = new RoutingPage(page);
      await page.goto('/favoritos');

      await expect(routing.loginDialog).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Iniciá sesión para continuar' })).toBeVisible();
      expect(routing.pathname()).toBe('/favoritos');
    },
  );

  test(
    'un estudiante autenticado entra directo a sus favoritos',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-007'] },
    async ({ page }) => {
      await loginAs(page);
      await page.goto('/favoritos');
      await expect(page.getByRole('heading', { name: 'Mis favoritos' })).toBeVisible();
      expect(new URL(page.url()).pathname).toBe('/favoritos');
    },
  );

  test(
    'un profesor no puede abrir favoritos por URL',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-008'] },
    async ({ page }) => {
      await loginAs(page, TEACHER);
      const routing = new RoutingPage(page);
      await page.goto('/favoritos');

      await expect(routing.accessDenied).toBeVisible();
      await expect(routing.cards).toHaveCount(0);
    },
  );

  test(
    'una conversación tiene URL propia y abre el hilo',
    { tag: ['@high', '@e2e', '@routing', '@ARCH-E2E-009'] },
    async ({ page }) => {
      await loginAs(page);
      const [teacher] = await publicTeachers(1);
      const conversationId = await ensureConversationWith(page, teacher.id, uniqueText('routing'));

      const routing = new RoutingPage(page);
      await page.goto(`/mensajes/${conversationId}`);
      await expect(routing.thread).toBeVisible();
      expect(routing.pathname()).toBe(`/mensajes/${conversationId}`);
    },
  );

  test(
    'una ruta desconocida muestra la página 404',
    { tag: ['@medium', '@e2e', '@routing', '@ARCH-E2E-010'] },
    async ({ page }) => {
      const routing = new RoutingPage(page);
      await page.goto('/ruta-que-no-existe');
      await expect(routing.notFound).toBeVisible();
      await expect(page.getByRole('link', { name: 'Volver al inicio' })).toBeVisible();
    },
  );
});
