import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ResetPasswordPage from './ResetPasswordPage';

const mocks = vi.hoisted(() => ({
  resetPassword: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      resetPassword: mocks.resetPassword,
    },
  };
});

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    mocks.resetPassword.mockReset();
  });

  it('translates an invalid or expired link to Spanish', async () => {
    const { ApiError } = await import('../api');
    mocks.resetPassword.mockRejectedValue(
      new ApiError(400, 'The reset link is invalid or has expired'),
    );

    render(
      <MemoryRouter initialEntries={['/restablecer?token=raw-token']}>
        <ResetPasswordPage onLogin={vi.fn()} />
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Nueva contraseña'), {
      target: { value: 'NewPassword123' },
    });
    fireEvent.change(screen.getByLabelText('Repetir contraseña'), {
      target: { value: 'NewPassword123' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));

    expect(
      await screen.findByText('El enlace de recuperación es inválido o ya venció. Pedí uno nuevo.'),
    ).toBeInTheDocument();
  });
});
