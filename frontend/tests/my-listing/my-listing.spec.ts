import { expect, test } from '@playwright/test';
import { loginAs, TEACHER, uniqueText } from '../helpers';
import { TeacherListingPage } from '../teacher-listing/teacher-listing-page';

/**
 * Patrones de acciones destructivas en "Mi anuncio" (DESIGN.md §12):
 * reversibles → toast con "Deshacer"; irreversibles → diálogo de confirmación.
 */
test.describe('Mi anuncio — acciones destructivas', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEACHER);
  });

  test(
    'quitar una materia ofrece Deshacer y la restaura',
    { tag: ['@e2e', '@teacher-listing', '@TEACHER-E2E-DESTRUCT-001'] },
    async ({ page }) => {
      const listing = new TeacherListingPage(page);
      await listing.open();

      const tag = listing.subjectTags.first();
      await expect(tag).toBeVisible();
      const subjectName = (await tag.innerText()).split('·')[0].trim();

      // Quitar: ejecuta de inmediato y ofrece Deshacer (reversible).
      await tag.getByRole('button', { name: /^Quitar / }).click();
      await expect(page.getByText(`${subjectName} quitada`)).toBeVisible();

      await page.getByRole('button', { name: 'Deshacer' }).click();
      await expect(page.getByText('Materia restaurada')).toBeVisible();
      await expect(listing.subjectTags.filter({ hasText: subjectName }).first()).toBeVisible();
    },
  );

  test(
    'quitar formación pide confirmación (no es reversible)',
    { tag: ['@e2e', '@teacher-listing', '@TEACHER-E2E-DESTRUCT-002'] },
    async ({ page }) => {
      const listing = new TeacherListingPage(page);
      await listing.open();

      const degree = uniqueText('Formación E2E');
      await listing.institution.fill('UTN');
      await listing.degree.fill(degree);
      // startYear is required by the API (CreateTeacherEducationRequest @NotNull).
      await listing.startYear.fill('2020');
      await listing.addEducation.click();
      await expect(page.getByText('Formación agregada')).toBeVisible();

      // Quitar: como pierde documentos/verificación asociados, pide confirmación.
      await page.getByRole('button', { name: `Quitar ${degree}` }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await dialog.getByRole('button', { name: 'Quitar formación' }).click();

      await expect(page.getByText('Formación quitada')).toBeVisible();
      await expect(page.getByText(degree)).toHaveCount(0);
    },
  );
});
