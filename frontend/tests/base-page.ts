import { expect, type Locator, type Page } from '@playwright/test';

/**
 * Parent of every page object. The app navigates through in-memory state (no router yet),
 * so every flow starts from the home page.
 */
export class BasePage {
  constructor(protected page: Page) {}

  async gotoHome(): Promise<void> {
    await this.page.goto('/');
    await expect(this.page.getByRole('button', { name: 'ClaseYa — inicio' })).toBeVisible();
  }

  /** True when the view stopped loading (no placeholders left). */
  async expectNoSkeletons(): Promise<void> {
    await expect(this.page.locator('.skeleton')).toHaveCount(0);
  }

  /** Number of skeletons currently rendered, used to assert a revisit does not flash. */
  skeletonCount(): Promise<number> {
    return this.page.locator('.skeleton').count();
  }

  /**
   * Fails when a container scrolls sideways: long text without spaces must wrap inside it
   * instead of pushing a horizontal scrollbar.
   */
  async expectNoHorizontalOverflow(locator: Locator): Promise<void> {
    const overflow = await locator.evaluate((element) => element.scrollWidth - element.clientWidth);
    expect(overflow, 'horizontal overflow in the container').toBeLessThanOrEqual(1);
  }
}
