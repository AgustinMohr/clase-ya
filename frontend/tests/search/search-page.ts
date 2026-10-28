import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

/**
 * Resultados de búsqueda: filtros laterales, grilla de tarjetas y paginador.
 * The root is scoped because the landing has its own search input with the same accessible name.
 */
export class SearchPage extends BasePage {
  readonly root: Locator;
  readonly term: Locator;
  readonly submit: Locator;
  readonly cards: Locator;
  readonly count: Locator;
  readonly emptyState: Locator;
  readonly seeAll: Locator;
  readonly applyButton: Locator;
  readonly clearFilters: Locator;
  readonly priceFrom: Locator;
  readonly onlineChip: Locator;
  readonly pageIndicator: Locator;
  readonly next: Locator;
  readonly previous: Locator;

  constructor(page: Page) {
    super(page);
    this.root = page.getByTestId('search-page');
    this.term = this.root.getByRole('combobox', { name: 'Buscar materia' });
    this.submit = this.root.getByRole('button', { name: 'Buscar', exact: true });
    this.cards = this.root.getByTestId('teacher-card');
    this.count = this.root.getByText(/profesor(es)? encontrado/);
    this.emptyState = this.root.getByText('Sin resultados con estos filtros');
    this.seeAll = this.root.getByRole('button', { name: 'Ver todos los profesores' });
    this.applyButton = this.root.getByRole('button', { name: /Aplicar filtros/ });
    this.clearFilters = this.root.getByRole('button', { name: 'Limpiar filtros' });
    this.priceFrom = this.root.getByLabel('Desde');
    this.onlineChip = this.root.getByRole('button', { name: 'Online' });
    this.pageIndicator = this.root.getByText(/Página \d+ de \d+/);
    this.next = this.root.getByRole('button', { name: 'Siguiente' });
    this.previous = this.root.getByRole('button', { name: 'Anterior' });
  }

  async searchFromLanding(term: string): Promise<void> {
    await this.page.getByRole('combobox', { name: 'Buscar materia' }).fill(term);
    await this.page.getByRole('button', { name: 'Buscar', exact: true }).click();
    // The landing stays mounted for a tick after the URL changes: wait for the search screen
    // itself before any scoped locator is used.
    await expect(this.root).toBeVisible();
  }

  /** The empty state offers the whole catalog, which is the widest result set available. */
  async openWholeCatalog(): Promise<void> {
    await this.searchFromLanding('zzzz');
    await this.seeAll.click();
    await expect(this.cards.first()).toBeVisible();
  }

  async totalResults(): Promise<number> {
    const text = (await this.count.textContent()) ?? '';
    return Number(text.match(/\d+/)?.[0] ?? 0);
  }
}
