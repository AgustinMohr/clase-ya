import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';

/**
 * Responsive smoke: the critical public pages must fit a common mobile viewport without
 * horizontal scroll, and the compact navbar must keep accessible names.
 */
test.describe('Experiencia mobile', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test(
    'landing y búsqueda se adaptan y el navbar mantiene nombres accesibles',
    { tag: ['@critical', '@e2e', '@responsive'] },
    async ({ page }) => {
      await page.goto('/');
      await expect(page.getByRole('heading', { level: 1 })).toContainText(/Encontr/i);
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth > window.innerWidth + 1,
        ),
      ).toBe(false);

      await loginAs(page);
      await page.goto('/buscar');
      await expect(page.getByRole('combobox', { name: 'Buscar materia' })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Aplicar filtros' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Mensajes' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Favoritos' })).toBeVisible();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth > window.innerWidth + 1,
        ),
      ).toBe(false);
    },
  );
});
