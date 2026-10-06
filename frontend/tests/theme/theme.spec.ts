import { expect, test } from '@playwright/test';
import { BasePage } from '../base-page';

test.describe('Tema', () => {
  test(
    'el esquema de color del documento sigue al modo',
    { tag: ['@high', '@e2e', '@theme', '@THEME-E2E-001'] },
    async ({ page }) => {
      // Pin the OS preference so the initial theme is deterministic (light).
      await page.emulateMedia({ colorScheme: 'light' });

      const home = new BasePage(page);
      await home.gotoHome();

      const colorScheme = () =>
        page.evaluate(() => getComputedStyle(document.documentElement).colorScheme);

      // `color-scheme` is what makes the native scrollbar render light/dark with the theme.
      await expect.poll(colorScheme).toBe('light');

      await page.getByRole('button', { name: 'Activar modo oscuro' }).click();
      await expect.poll(colorScheme).toBe('dark');

      await page.getByRole('button', { name: 'Activar modo claro' }).click();
      await expect.poll(colorScheme).toBe('light');
    },
  );
});
