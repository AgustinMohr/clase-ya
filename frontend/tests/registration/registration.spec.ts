import { expect, test } from '@playwright/test';

/**
 * AUTH-001: the password path is complete end to end. The activation link itself is delivered by
 * email (logged in E2E), so the browser covers the registration/pending screens and the activation
 * error/resend paths; the valid-token transition is covered by the backend integration suite.
 */
test.describe('Registro y activación de cuenta', () => {
  test(
    'un registro válido deja la cuenta pendiente y muestra el aviso de email',
    { tag: ['@high', '@e2e', '@auth', '@AUTH-E2E-001'] },
    async ({ page }) => {
      const email = `e2e-register-${Date.now()}@example.com`;

      await page.goto('/registro');
      await page.getByLabel('Email').fill(email);
      await page.getByLabel('Contraseña', { exact: true }).fill('Password123');
      await page.getByLabel('Repetir contraseña').fill('Password123');
      await page.getByRole('button', { name: 'Crear cuenta' }).click();

      await expect(page.getByRole('heading', { name: 'Revisá tu email' })).toBeVisible();
      await expect(page.getByText(email)).toBeVisible();
      expect(new URL(page.url()).pathname).toBe('/registro');
    },
  );

  test(
    'el formulario rechaza contraseñas distintas sin llamar a la API',
    { tag: ['@medium', '@e2e', '@auth', '@AUTH-E2E-002'] },
    async ({ page }) => {
      await page.goto('/registro');
      await page.getByLabel('Email').fill(`e2e-mismatch-${Date.now()}@example.com`);
      await page.getByLabel('Contraseña', { exact: true }).fill('Password123');
      await page.getByLabel('Repetir contraseña').fill('Password124');
      await page.getByRole('button', { name: 'Crear cuenta' }).click();

      await expect(page.getByText('Las contraseñas no coinciden.')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Revisá tu email' })).toHaveCount(0);
    },
  );

  test(
    'la página de activación sin token explica el problema y ofrece reenviar',
    { tag: ['@medium', '@e2e', '@auth', '@AUTH-E2E-003'] },
    async ({ page }) => {
      await page.goto('/activar');

      await expect(page.getByRole('heading', { name: 'No pudimos activar la cuenta' })).toBeVisible();
      await expect(page.getByText(/Falta el token/)).toBeVisible();
      await expect(page.getByLabel('Email de la cuenta')).toBeVisible();
    },
  );

  test(
    'un token inválido no activa nada y el reenvío responde de forma genérica',
    { tag: ['@high', '@e2e', '@auth', '@AUTH-E2E-004'] },
    async ({ page }) => {
      await page.goto('/activar?token=token-invalido');

      await expect(page.getByRole('heading', { name: 'No pudimos activar la cuenta' })).toBeVisible();
      await page.getByLabel('Email de la cuenta').fill(`e2e-resend-${Date.now()}@example.com`);
      await page.getByRole('button', { name: 'Reenviar enlace' }).click();

      await expect(
        page.getByText(/Si el email corresponde a una cuenta pendiente/),
      ).toBeVisible();
    },
  );

  test(
    'el modal de ingreso enlaza al registro',
    { tag: ['@medium', '@e2e', '@auth', '@AUTH-E2E-005'] },
    async ({ page }) => {
      await page.goto('/');
      await page.getByRole('button', { name: 'Ingresar' }).click();
      await expect(page.getByRole('dialog')).toBeVisible();

      await page.getByRole('button', { name: 'Crear cuenta' }).click();

      await expect(page).toHaveURL(/\/registro$/);
      await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible();
    },
  );
});
