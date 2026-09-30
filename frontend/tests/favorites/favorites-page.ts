import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';

/** "Mis favoritos": los profesores guardados por el estudiante. */
export class FavoritesPage extends BasePage {
  readonly heading: Locator;
  readonly cards: Locator;
  readonly emptyState: Locator;

  constructor(page: Page) {
    super(page);
    this.heading = page.getByRole('heading', { name: 'Mis favoritos' });
    this.cards = page.getByTestId('teacher-card');
    this.emptyState = page.getByText('Todavía no guardaste profesores');
  }

  async openFromNavbar(): Promise<void> {
    // `exact` matters: the favourite toggles are also "…favoritos" buttons.
    await this.page.getByRole('button', { name: 'Favoritos', exact: true }).click();
    await expect(this.heading).toBeVisible();
  }

  cardOf(teacherName: string): Locator {
    return this.cards.filter({ hasText: teacherName });
  }
}
