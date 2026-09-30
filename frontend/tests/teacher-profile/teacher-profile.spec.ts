import { expect, test } from '@playwright/test';
import { TEACHER, firstPublicTeacher, loginAs, uniqueText } from '../helpers';
import { MessagesPage } from '../messages/messages-page';
import { TeacherProfilePage } from './teacher-profile-page';

test.describe('Perfil publico del profesor y contacto', () => {
  test(
    'contactar crea la conversación con el primer mensaje y abre el hilo',
    { tag: ['@critical', '@e2e', '@contact', '@CONTACT-E2E-001'] },
    async ({ page }) => {
      await loginAs(page);
      await firstPublicTeacher();

      const profile = new TeacherProfilePage(page);
      await profile.gotoHome();
      await profile.openFirstCard();

      const message = await profile.sendContactRequest();

      // Landing on Mensajes with the conversation open and the message already there is
      // exactly what broke when the backend ignored the new `message` field.
      await expect(page.getByRole('heading', { name: 'Mensajes' })).toBeVisible();
      const messages = new MessagesPage(page);
      await messages.expectBubbleWith(message);
    },
  );

  test(
    'un profesor no puede contactar ni guardar favoritos',
    { tag: ['@high', '@e2e', '@contact', '@CONTACT-E2E-002'] },
    async ({ page }) => {
      await loginAs(page, TEACHER);

      const profile = new TeacherProfilePage(page);
      await profile.gotoHome();
      await profile.openFirstCard();

      expect(await profile.contactButtonIsVisible(), 'Contactar no debe existir para un profesor').toBe(false);
      await expect(page.getByText(/los contactos los inician los estudiantes/)).toBeVisible();
      expect(await profile.favoriteButton.isVisible(), 'el boton de favoritos no debe existir').toBe(false);
    },
  );

  test(
    'la materia del contacto se elige entre las del profesor',
    { tag: ['@medium', '@e2e', '@contact', '@CONTACT-E2E-003'] },
    async ({ page }) => {
      await loginAs(page);
      const profile = new TeacherProfilePage(page);
      await profile.gotoHome();
      await profile.openFirstCard();

      await profile.contactButton.click();
      const subject = profile.dialog.getByLabel('Materia');
      const options = await subject.locator('option').allTextContents();
      expect(options.length, 'el select trae materias reales, no texto libre').toBeGreaterThan(0);
      expect(options[0]).not.toContain(uniqueText('')); // nombres reales del catalogo
      await profile.dialog.getByRole('button', { name: 'Cancelar' }).click();
    },
  );
});
