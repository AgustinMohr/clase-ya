import { expect, test, type APIRequestContext } from '@playwright/test';
import { ADMIN, TEACHER, loginAs, uniqueText } from '../helpers';

async function csrf(context: APIRequestContext): Promise<string> {
  const response = await context.get('/api/auth/csrf');
  if (!response.ok()) {
    throw new Error(`CSRF token failed: ${response.status()}`);
  }
  return ((await response.json()) as { token: string }).token;
}

/**
 * VERIF-001: the admin marks the evidence as reviewed and then verifies the credential. The gate
 * (every document reviewed) is what makes the checkbox meaningful.
 */
test.describe('Cola de revisión de documentación', () => {
  test(
    'el admin revisa el documento y verifica la credencial',
    { tag: ['@e2e', '@admin', '@ADMIN-E2E-004'] },
    async ({ page }) => {
      const degree = uniqueText('Verificación E2E');
      const fileName = `diploma-${Date.now()}.pdf`;

      // A teacher presents a credential with one PDF (the same flow the UI uses).
      await loginAs(page, TEACHER);
      const api = page.context().request;
      const education = await api.post('/api/teachers/me/education', {
        data: { institution: 'UTN E2E', degree, startYear: 2010 },
        headers: { 'X-XSRF-TOKEN': await csrf(api) },
      });
      if (!education.ok()) {
        throw new Error(`Creating the credential failed: ${education.status()}`);
      }
      const educationId = ((await education.json()) as { id: string }).id;

      const upload = await api.post(`/api/teachers/me/education/${educationId}/documents`, {
        multipart: {
          type: 'DIPLOMA',
          file: {
            name: fileName,
            mimeType: 'application/pdf',
            buffer: Buffer.from('%PDF-1.4\nE2E verification\n'),
          },
        },
        headers: { 'X-XSRF-TOKEN': await csrf(api) },
      });
      expect(upload.status(), await upload.text()).toBe(201);

      const submit = await api.post(`/api/teachers/me/education/${educationId}/submit`, {
        headers: { 'X-XSRF-TOKEN': await csrf(api) },
      });
      expect(submit.ok(), await submit.text()).toBeTruthy();

      // The admin reviews it.
      await loginAs(page, ADMIN);
      await page.goto('/admin/verificacion');

      await page.getByRole('button', { name: new RegExp(degree) }).click();
      const card = page.getByTestId('admin-credential').filter({ hasText: degree });
      await expect(card.getByText(fileName)).toBeVisible();
      await expect(card.getByText('Sin escanear')).toBeVisible();

      // Controlled checkbox: it only flips after the refetch, so click and wait for the review text.
      await card.getByLabel(`Marcar ${fileName} como revisado`).click();
      await expect(card.getByText(/Revisado por/)).toBeVisible();

      await card.getByRole('button', { name: 'Decidir' }).click();
      await card.getByRole('button', { name: 'Registrar decisión' }).click();

      await expect(page.getByText('Decisión registrada')).toBeVisible();
    },
  );
});
