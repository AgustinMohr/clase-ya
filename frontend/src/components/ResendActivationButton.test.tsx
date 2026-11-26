import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import { useAuth } from '../auth/AuthContext';
import ResendActivationButton, { RESEND_COOLDOWN_SECONDS } from './ResendActivationButton';

vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }));

const resendActivation = vi.fn();

async function wait(seconds: number) {
  // One tick per second: each timeout is scheduled by the render that follows the previous one.
  for (let i = 0; i < seconds; i += 1) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
  }
}

describe('ResendActivationButton', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    resendActivation.mockReset().mockResolvedValue(undefined);
    vi.mocked(useAuth).mockReturnValue({ resendActivation } as unknown as ReturnType<
      typeof useAuth
    >);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('stays disabled during the first 30 seconds', async () => {
    render(<ResendActivationButton email="ada@example.com" />);

    expect(screen.getByRole('button', { name: 'Reenviar correo (30 s)' })).toBeDisabled();
    await wait(RESEND_COOLDOWN_SECONDS - 1);
    expect(screen.getByRole('button', { name: 'Reenviar correo (1 s)' })).toBeDisabled();
    await wait(1);
    expect(screen.getByRole('button', { name: 'Reenviar correo' })).toBeEnabled();
    expect(resendActivation).not.toHaveBeenCalled();
  });

  it('resends once and restarts the wait', async () => {
    render(<ResendActivationButton email="ada@example.com" />);
    await wait(RESEND_COOLDOWN_SECONDS);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));
    });

    expect(resendActivation).toHaveBeenCalledTimes(1);
    expect(resendActivation).toHaveBeenCalledWith('ada@example.com');
    expect(screen.getByRole('status')).toHaveTextContent('te enviamos un enlace nuevo');
    expect(screen.getByRole('button', { name: 'Reenviar correo (30 s)' })).toBeDisabled();
  });

  it.each([
    [429, 'demasiados enlaces'],
    [503, 'No pudimos reenviar'],
  ])('explains a %i failure and restarts the wait', async (status, text) => {
    resendActivation.mockRejectedValue(new ApiError(status, 'error'));
    render(<ResendActivationButton email="ada@example.com" />);
    await wait(RESEND_COOLDOWN_SECONDS);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Reenviar correo' }));
    });

    expect(screen.getByRole('status')).toHaveTextContent(text);
    expect(screen.getByRole('button', { name: 'Reenviar correo (30 s)' })).toBeDisabled();
  });
});
