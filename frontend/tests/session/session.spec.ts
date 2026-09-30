import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';
import { MessagesPage } from '../messages/messages-page';
import { SearchPage } from '../search/search-page';

test.describe('Sesion y navegacion', () => {
  test(
    'cerrar sesión desde Mensajes vuelve a la landing',
    { tag: ['@high', '@e2e', '@session', '@SESSION-E2E-001'] },
    async ({ page }) => {
      await loginAs(page);
      const messages = new MessagesPage(page);
      await messages.gotoHome();
      await messages.open();

      await page.getByRole('button', { name: 'Salir' }).click();

      // Private views must not survive the session (the view is reset on logout).
      await expect(page.getByRole('heading', { name: /Encontrá tu profesor/ })).toBeVisible();
      await expect(messages.heading).toHaveCount(0);
    },
  );

  test(
    'abrir un perfil desde una lista scrolleada deja el scroll arriba',
    { tag: ['@medium', '@e2e', '@session', '@SESSION-E2E-002'] },
    async ({ page }) => {
      await loginAs(page);
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.openWholeCatalog();

      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      expect(await page.evaluate(() => window.scrollY), 'la lista debe quedar scrolleada').toBeGreaterThan(0);

      await search.cards.last().getByRole('button', { name: 'Ver perfil' }).click();
      await expect(page.getByRole('button', { name: 'Contactar' })).toBeVisible();

      expect(await page.evaluate(() => window.scrollY), 'el perfil debe abrir arriba').toBe(0);
    },
  );
});
