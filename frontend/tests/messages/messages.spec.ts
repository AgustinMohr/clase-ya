import { expect, test } from '@playwright/test';
import { ensureConversationWith, loginAs, publicTeachers, uniqueText, type TeacherRef } from '../helpers';
import { MessagesPage } from './messages-page';

test.describe('Mensajes', () => {
  let teacherA: TeacherRef;
  let teacherB: TeacherRef;

  test.beforeEach(async ({ page }) => {
    const token = await loginAs(page);
    [teacherA, teacherB] = await publicTeachers(2);
    // Preconditions through the API: the list always has something to open, both threads
    // have content, and no assertion depends on leftovers from a previous run.
    await ensureConversationWith(token, teacherA.id, uniqueText('precondición A'));
    await ensureConversationWith(token, teacherB.id, uniqueText('precondición B'));
  });

  test(
    'un mensaje largo sin espacios no genera scroll horizontal',
    { tag: ['@critical', '@e2e', '@messages', '@MSG-E2E-001'] },
    async ({ page }) => {
      const messages = new MessagesPage(page);
      await messages.gotoHome();
      await messages.open();
      await messages.openConversation(teacherA.displayName);

      await messages.sendUnbreakableText();

      await messages.expectNoHorizontalOverflow(messages.threadBody);
      await messages.expectNoHorizontalOverflow(messages.bubbles.last());
    },
  );

  test(
    'Enter envía el mensaje y Shift+Enter agrega una línea',
    { tag: ['@critical', '@e2e', '@messages', '@MSG-E2E-002'] },
    async ({ page }) => {
      const messages = new MessagesPage(page);
      await messages.gotoHome();
      await messages.open();
      await messages.openConversation(teacherA.displayName);

      const firstLine = uniqueText('primera linea');
      const secondLine = uniqueText('segunda linea');
      await messages.composer.fill(firstLine);
      await messages.composer.press('Shift+Enter');
      await messages.composer.pressSequentially(secondLine);
      await messages.composer.press('Enter');

      const bubble = messages.bubbles.filter({ hasText: firstLine }).first();
      await expect(bubble).toBeVisible();
      // The line break stayed inside the same message instead of sending two.
      await expect(bubble).toContainText(secondLine);
      await expect(messages.composer).toHaveValue('');
    },
  );

  test(
    'volver a una conversación ya vista no parpadea con skeletons',
    { tag: ['@high', '@e2e', '@messages', '@MSG-E2E-003'] },
    async ({ page }) => {
      const messages = new MessagesPage(page);
      await messages.gotoHome();
      await messages.open();

      await messages.openConversation(teacherA.displayName);
      await expect(messages.bubbles.first()).toBeVisible();
      await messages.openConversation(teacherB.displayName);
      await expect(messages.bubbles.first()).toBeVisible();

      // Revisit: the thread renders from cache, so no placeholder may appear. Checked
      // without retrying, otherwise a flash would be tolerated.
      await messages.conversation(teacherA.displayName).click();
      await expect(messages.bubbles.first()).toBeVisible();
      expect(await messages.skeletonCount(), 'la conversación revisitada no debe parpadear').toBe(0);
    },
  );
});
