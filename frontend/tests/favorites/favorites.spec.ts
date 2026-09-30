import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';
import { FavoritesPage } from './favorites-page';

test.describe('Favoritos', () => {
  test(
    'guardar y quitar un favorito se refleja en la lista',
    { tag: ['@high', '@e2e', '@favorites', '@FAV-E2E-001'] },
    async ({ page }) => {
      await loginAs(page);
      const favorites = new FavoritesPage(page);
      await favorites.gotoHome();

      // A teacher of the landing that is not saved yet, so the flow starts from a known state.
      const saveButton = page.getByRole('button', { name: /^Guardar a .* en favoritos$/ }).first();
      await expect(saveButton, 'el seed debe dejar al menos un profesor sin guardar').toBeVisible();
      const label = (await saveButton.getAttribute('aria-label')) ?? '';
      const teacher = label.replace('Guardar a ', '').replace(' en favoritos', '');

      await saveButton.click();
      await expect(page.getByRole('button', { name: `Quitar a ${teacher} de favoritos` }).first()).toBeVisible();

      await favorites.openFromNavbar();
      await expect(favorites.cardOf(teacher)).toHaveCount(1);

      // Removing from the favorites page cleans up after itself: the state is restored.
      await page.getByRole('button', { name: `Quitar a ${teacher} de favoritos` }).first().click();
      await expect(favorites.cardOf(teacher)).toHaveCount(0);
    },
  );
});
