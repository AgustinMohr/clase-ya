import { expect, test } from '@playwright/test';
import { ADMIN, loginAs, STUDENT } from '../helpers';

/**
 * ADMIN-001: el panel solo existe para una sesión ADMIN. El guard del SPA es UX; la API sigue
 * exigiendo ROLE_ADMIN sobre `/api/admin/**`.
 */
test.describe('Panel de administración (shell)', () => {
  test(
    'un admin entra al panel y ve su entrada en el navbar',
    { tag: ['@e2e', '@admin', '@ADMIN-E2E-001'] },
    async ({ page }) => {
      await loginAs(page, ADMIN);
      await page.goto('/admin');

      await expect(page.getByRole('heading', { name: 'Administración' })).toBeVisible();
      await expect(page.getByRole('navigation', { name: 'Administración' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Resumen' })).toHaveAttribute('aria-current', 'page');
      await expect(page.getByRole('link', { name: 'Admin' })).toBeVisible();
      await expect(page.getByText('Sin módulos todavía')).toBeVisible();
    },
  );

  test(
    'un estudiante no ve el panel ni la entrada del navbar',
    { tag: ['@e2e', '@admin', '@ADMIN-E2E-002'] },
    async ({ page }) => {
      await loginAs(page, STUDENT);
      await page.goto('/admin');

      await expect(page.getByText(/No tenés acceso/)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Administración' })).toHaveCount(0);
      await expect(page.getByRole('link', { name: 'Admin' })).toHaveCount(0);
    },
  );
});
