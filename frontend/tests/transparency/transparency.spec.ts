import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';
import { SearchPage } from '../search/search-page';

test.describe('Transparencia pública', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test(
    'las tarjetas muestran el estado de verificación',
    { tag: ['@e2e', '@transparency', '@TEACHER-C-E2E-001'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.openWholeCatalog();

      // Toda tarjeta lleva un badge de verificación (D1).
      await expect(search.cards.first()).toBeVisible();
      await expect(page.getByText('Verificado', { exact: true }).first()).toBeVisible();
    },
  );

  test(
    'el filtro "Solo verificados" deja solo profesores verificados',
    { tag: ['@critical', '@e2e', '@transparency', '@TEACHER-C-E2E-002'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.openWholeCatalog();
      expect(await search.totalResults()).toBeGreaterThan(0);

      await page.getByRole('button', { name: 'Solo verificados' }).click();
      await search.applyButton.click();

      await expect(search.cards.first()).toBeVisible();
      // Ninguna tarjeta puede quedar etiquetada "No verificado".
      await expect(page.getByText('No verificado', { exact: true })).toHaveCount(0);
    },
  );
});
