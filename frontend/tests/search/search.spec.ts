import { expect, test } from '@playwright/test';
import { loginAs } from '../helpers';
import { SearchPage } from './search-page';

test.describe('Búsqueda de profesores', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page);
  });

  test(
    'el catálogo completo se pagina',
    { tag: ['@critical', '@e2e', '@search', '@SEARCH-E2E-001'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.openWholeCatalog();

      const total = await search.totalResults();
      expect(total, 'el seed demo debe aportar un catálogo paginable').toBeGreaterThan(24);

      await expect(search.pageIndicator).toHaveText('Página 1 de 3');
      const firstPageNames = await search.cards.locator('h3').allTextContents();

      await search.next.click();
      await expect(search.pageIndicator).toHaveText('Página 2 de 3');
      const secondPageNames = await search.cards.locator('h3').allTextContents();

      // Pages must not repeat the same teachers.
      expect(secondPageNames.some((name) => firstPageNames.includes(name))).toBe(false);

      await search.previous.click();
      await expect(search.pageIndicator).toHaveText('Página 1 de 3');
    },
  );

  test(
    'los filtros se aplican solo al presionar el botón',
    { tag: ['@critical', '@e2e', '@search', '@SEARCH-E2E-002'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.openWholeCatalog();
      const before = await search.totalResults();
      expect(before).toBeGreaterThan(0);

      // Written in the price field without applying: the results must not move.
      await search.priceFrom.fill('30000');
      await expect(search.count).toHaveText(new RegExp(`${before} profesores? encontrados?`));

      // Applying the filter is what queries again: above the demo prices nothing matches.
      await search.applyButton.click();
      await expect(search.emptyState).toBeVisible();

      // Clearing brings the previous set back.
      await search.clearFilters.click();
      await expect(search.count).toHaveText(new RegExp(`${before} profesores? encontrados?`));
    },
  );

  test(
    'una materia sin profesores muestra el estado vacío',
    { tag: ['@medium', '@e2e', '@search', '@SEARCH-E2E-003'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      await search.searchFromLanding('Química General');
      await expect(search.emptyState).toBeVisible();
    },
  );

  test(
    'elegir una sugerencia escribe la materia real y los filtros la respetan',
    { tag: ['@critical', '@e2e', '@search', '@SEARCH-E2E-004'] },
    async ({ page }) => {
      const search = new SearchPage(page);
      await search.gotoHome();
      // Land on the search page first (works even when the term matches nothing).
      await search.searchFromLanding('zzzz');

      // Type a partial term: the suggestions carry the full subject name from the catalog.
      await search.term.fill('mate');
      const option = page.getByRole('listbox', { name: 'Sugerencias' }).getByRole('option').first();
      await expect(option).toBeVisible();
      const subjectName = (await option.innerText()).trim();
      await option.getByRole('button').click();

      // The input must now show the catalog subject, not the partial text that was typed.
      await expect(search.term).toHaveValue(subjectName);

      // Applying a filter keeps searching the resolved subject. Regression: it used to re-run
      // with the partial term ("mate") and show "No encontramos".
      await search.applyButton.click();
      await expect(page.getByText(/No encontramos/)).toHaveCount(0);
    },
  );
});
