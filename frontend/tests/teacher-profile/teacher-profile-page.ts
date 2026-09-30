import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';
import { uniqueText } from '../helpers';

/**
 * Public teacher profile plus the contact request dialog (CONTACT-001).
 */
export class TeacherProfilePage extends BasePage {
  readonly heading: Locator;
  readonly contactButton: Locator;
  readonly favoriteButton: Locator;
  readonly dialog: Locator;
  readonly messageField: Locator;
  readonly sendButton: Locator;

  constructor(page: Page) {
    super(page);
    this.heading = page.getByRole('heading', { level: 1 });
    this.contactButton = page.getByRole('button', { name: 'Contactar' });
    this.favoriteButton = page.getByRole('button', { name: /favoritos/ });
    this.dialog = page.getByRole('dialog');
    this.messageField = this.dialog.getByLabel('Mensaje');
    this.sendButton = this.dialog.getByRole('button', { name: 'Enviar mensaje' });
  }

  /** The landing shows the same teachers, in the same order, as the public listing. */
  async openFirstCard(): Promise<void> {
    await this.page.getByTestId('teacher-card').first().click();
    await expect(this.contactButton.or(this.page.getByText(/estudiantes/))).toBeVisible();
  }

  contactButtonIsVisible(): Promise<boolean> {
    return this.contactButton.isVisible();
  }

  /** Sends a contact request with a unique message and returns the text used. */
  async sendContactRequest(note = 'Hola, busco apoyo'): Promise<string> {
    const message = uniqueText(note);
    await this.contactButton.click();
    await expect(this.dialog).toBeVisible();
    await this.messageField.fill(message);
    await this.sendButton.click();
    return message;
  }
}
