import { expect, test } from '@playwright/test';
import { ADMIN, STUDENT, loginAs } from '../helpers';

/**
 * ADMIN-002: el directorio filtra cuentas y permite suspender/restaurar. Suspender tiene efecto
 * inmediato porque el backend recarga la cuenta en cada request.
 */
test.describe('Directorio de usuarios', () => {
  test(
    'el admin busca una cuenta, la suspende y la restaura',
    { tag: ['@e2e', '@admin', '@ADMIN-E2E-003'] },
    async ({ page }) => {
      await loginAs(page, ADMIN);
      await page.goto('/admin/usuarios');

      await page.getByLabel('Buscar').fill(STUDENT);
      await page.getByRole('button', { name: 'Buscar' }).click();

      const row = page.getByRole('listitem').filter({ hasText: STUDENT }).first();
      await expect(row).toBeVisible();

      await row.getByRole('button', { name: 'Suspender' }).click();
      await expect(page.getByText('Cuenta suspendida')).toBeVisible();
      await expect(row.getByText('Suspendida')).toBeVisible();

      await row.getByRole('button', { name: 'Restaurar' }).click();
      await expect(page.getByText('Cuenta restaurada')).toBeVisible();
      await expect(row.getByText('Activa')).toBeVisible();
    },
  );
});
