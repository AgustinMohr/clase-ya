import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HumanGate from './HumanGate';
import { api } from '../api';

vi.mock('../api', () => ({
  api: {
    humanStatus: vi.fn(),
    humanVerify: vi.fn(),
  },
}));

vi.mock('./TurnstileWidget', () => ({
  default: ({ onToken }: { onToken: (token: string) => void }) => (
    <button type="button" onClick={() => onToken('turnstile-token')}>Resolver captcha</button>
  ),
}));

describe('HumanGate', () => {
  beforeEach(() => {
    vi.mocked(api.humanStatus).mockReset();
    vi.mocked(api.humanVerify).mockReset();
  });

  it('renders the app when no human check is required', async () => {
    vi.mocked(api.humanStatus).mockResolvedValue({ required: false, verified: false });

    render(<HumanGate><p>Contenido privado</p></HumanGate>);

    expect(await screen.findByText('Contenido privado')).toBeInTheDocument();
  });

  it('verifies the first visit and then renders the app', async () => {
    vi.mocked(api.humanStatus).mockResolvedValue({ required: true, verified: false });
    vi.mocked(api.humanVerify).mockResolvedValue(undefined);

    render(<HumanGate><p>Contenido privado</p></HumanGate>);

    expect(await screen.findByRole('heading', { name: 'Antes de continuar' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Resolver captcha' }));

    await waitFor(() => expect(api.humanVerify).toHaveBeenCalledWith('turnstile-token'));
    expect(await screen.findByText('Contenido privado')).toBeInTheDocument();
  });

  it('shows a retry when the status request fails', async () => {
    vi.mocked(api.humanStatus).mockRejectedValue(new Error('network'));

    render(<HumanGate><p>Contenido privado</p></HumanGate>);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos verificar tu acceso');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
