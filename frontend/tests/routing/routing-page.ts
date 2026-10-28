import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

/** Shared locators for the routing/deep-link scenarios. */
export class RoutingPage extends BasePage {
  readonly term: Locator;
  readonly applyButton: Locator;
  readonly onlineChip: Locator;
  readonly cards: Locator;
  readonly loginDialog: Locator;
  readonly accessDenied: Locator;
  readonly notFound: Locator;
  readonly thread: Locator;

  constructor(page: Page) {
    super(page);
    this.term = page.getByRole('combobox', { name: 'Buscar materia' });
    this.applyButton = page.getByRole('button', { name: /Aplicar filtros/ });
    this.onlineChip = page.getByRole('button', { name: 'Online' });
    this.cards = page.getByTestId('teacher-card');
    this.loginDialog = page.getByRole('dialog', { name: 'Ingresar' });
    this.accessDenied = page.getByRole('heading', { name: 'No tenés acceso a esta sección' });
    this.notFound = page.getByRole('heading', { name: 'Página no encontrada' });
    this.thread = page.getByTestId('thread-shell');
  }

  async openSearch(query = ''): Promise<void> {
    await this.page.goto(`/buscar${query}`);
    await expect(this.applyButton).toBeVisible();
  }

  pathname(): string {
    return new URL(this.page.url()).pathname;
  }
}
