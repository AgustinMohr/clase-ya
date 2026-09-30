import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

/**
 * Resultados de búsqueda: filtros laterales, grilla de tarjetas y paginador.
 */
export class SearchPage extends BasePage {
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
    this.term = page.getByRole('combobox', { name: 'Buscar materia' });
    this.submit = page.getByRole('button', { name: 'Buscar' });
    this.cards = page.getByTestId('teacher-card');
    this.count = page.getByText(/profesor(es)? encontrado/);
    this.emptyState = page.getByText('Sin resultados con estos filtros');
    this.seeAll = page.getByRole('button', { name: 'Ver todos los profesores' });
    this.applyButton = page.getByRole('button', { name: /Aplicar filtros/ });
    this.clearFilters = page.getByRole('button', { name: 'Limpiar filtros' });
    this.priceFrom = page.getByLabel('Desde');
    this.onlineChip = page.getByRole('button', { name: 'Online' });
    this.pageIndicator = page.getByText(/Página \d+ de \d+/);
    this.next = page.getByRole('button', { name: 'Siguiente' });
    this.previous = page.getByRole('button', { name: 'Anterior' });
  }

  async searchFromLanding(term: string): Promise<void> {
    await this.term.fill(term);
    await this.submit.click();
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
