import { expect, test } from '@playwright/test';
import { loginAs, TEACHER, uniqueText } from '../helpers';
import { TeacherListingPage } from './teacher-listing-page';

test.describe('Mi anuncio (profesor)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEACHER);
  });

  test(
    'el profesor ve su anuncio publicado y edita la bio',
    { tag: ['@critical', '@e2e', '@teacher-listing', '@TEACHER-A-E2E-001'] },
    async ({ page }) => {
      const listing = new TeacherListingPage(page);
      await listing.open();

      // El profesor de demo tiene un anuncio completo.
      await expect(listing.publishedBadge).toBeVisible();

      // La bio viene precargada; su contenido depende del seed y de corridas previas.
      const currentBio = await listing.bio.inputValue();
      expect(currentBio.trim().length).toBeGreaterThan(0);

      // The textarea caps the bio at the backend maximum (2000) to avoid a 400 on save.
      await expect(listing.bio).toHaveAttribute('maxlength', '2000');

      const newBio = uniqueText('Bio E2E');
      await listing.bio.fill(newBio);
      await listing.saveProfile.click();
      await expect(page.getByText('Perfil guardado')).toBeVisible();
      await expect(listing.bio).toHaveValue(newBio);
    },
  );

  test(
    'el profesor sube un documento y presenta la credencial',
    { tag: ['@critical', '@e2e', '@teacher-listing', '@TEACHER-B-E2E-001'] },
    async ({ page }) => {
      const listing = new TeacherListingPage(page);
      await listing.open();

      const degree = uniqueText('Ingeniero en Sistemas E2E');
      await listing.institution.fill('UTN');
      await listing.degree.fill(degree);
      await listing.startYear.fill('2020');
      await listing.addEducation.click();
      await expect(page.getByText('Formación agregada')).toBeVisible();

      // La credencial nueva aparece en el panel de verificación.
      const row = listing.credentialRow(degree);
      await expect(row).toBeVisible();

      await row.getByLabel('Archivo (PDF, JPG o PNG)').setInputFiles({
        name: 'diploma.pdf',
        mimeType: 'application/pdf',
        buffer: Buffer.from('%PDF-1.4\ncontenido de prueba E2E\n'),
      });
      await row.getByRole('button', { name: 'Subir' }).click();
      await expect(page.getByText('Documento subido')).toBeVisible();

      // Presentarla la deja en revisión (submit).
      await row.getByRole('button', { name: 'Presentar a revisión' }).click();
      await expect(row.getByText('En revisión', { exact: true })).toBeVisible();
    },
  );
});
