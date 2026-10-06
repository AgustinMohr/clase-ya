import { expect, type Locator, type Page } from '@playwright/test';
import { BasePage } from '../base-page';
import { uniqueText } from '../helpers';

/**
 * Mensajes view: conversation list + thread + composer.
 */
export class MessagesPage extends BasePage {
  readonly navButton: Locator;
  readonly heading: Locator;
  readonly threadShell: Locator;
  readonly threadBody: Locator;
  readonly composer: Locator;
  readonly counter: Locator;
  readonly sendButton: Locator;
  readonly bubbles: Locator;

  constructor(page: Page) {
    super(page);
    this.navButton = page.getByRole('button', { name: 'Mensajes' });
    this.heading = page.getByRole('heading', { name: 'Mensajes' });
    this.threadShell = page.getByTestId('thread-shell');
    this.threadBody = page.getByTestId('thread-body');
    this.composer = page.getByLabel('Escribí un mensaje');
    this.counter = page.getByTestId('composer-counter');
    this.sendButton = page.getByRole('button', { name: 'Enviar' });
    this.bubbles = page.getByTestId('message-bubble');
  }

  async open(): Promise<void> {
    await this.navButton.click();
    await expect(this.heading).toBeVisible();
  }

  conversation(name: string): Locator {
    return this.page.getByRole('button', { name: new RegExp(name) }).first();
  }

  async openConversation(name: string): Promise<void> {
    await this.conversation(name).click();
    await expect(this.composer).toBeVisible();
  }

  async send(text: string): Promise<void> {
    await this.composer.fill(text);
    await this.sendButton.click();
    await this.expectBubbleWith(text);
  }

  async sendWithEnter(text: string): Promise<void> {
    await this.composer.fill(text);
    await this.composer.press('Enter');
    await this.expectBubbleWith(text);
  }

  async expectBubbleWith(text: string): Promise<void> {
    await expect(this.bubbles.filter({ hasText: text }).first()).toBeVisible();
  }

  /** Writes a text with an unbreakable tail, the case that used to scroll the thread sideways. */
  async sendUnbreakableText(tailLength = 220): Promise<string> {
    const text = uniqueText(`mensaje ${'a'.repeat(tailLength)}`);
    await this.send(text);
    return text;
  }
}
