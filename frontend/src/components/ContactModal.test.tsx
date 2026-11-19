import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ContactModal from './ContactModal';

const mocks = vi.hoisted(() => ({
  startConversation: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      startConversation: mocks.startConversation,
    },
  };
});

const teacher = {
  id: 'teacher-1',
  displayName: 'Bruno',
  subjects: [{ subjectName: 'Matemática' }],
};

function renderModal() {
  return render(
    <ContactModal
      open
      teacher={teacher}
      onClose={vi.fn()}
      onSent={vi.fn()}
      onNeedsProfile={vi.fn()}
    />,
  );
}

describe('ContactModal errors', () => {
  beforeEach(() => {
    mocks.startConversation.mockReset();
  });

  it('explains a blocked contact', async () => {
    const { ApiError } = await import('../api');
    mocks.startConversation.mockRejectedValue(new ApiError(403, 'Forbidden'));

    renderModal();
    fireEvent.click(screen.getByRole('button', { name: 'Enviar mensaje' }));

    expect(await screen.findByText('No podés contactar a este usuario.')).toBeInTheDocument();
  });

  it('explains the contact rate limit', async () => {
    const { ApiError } = await import('../api');
    mocks.startConversation.mockRejectedValue(new ApiError(429, 'Too Many Requests'));

    renderModal();
    fireEvent.click(screen.getByRole('button', { name: 'Enviar mensaje' }));

    expect(
      await screen.findByText(
        'Hiciste muchos contactos seguidos. Esperá unos minutos e intentá de nuevo.',
      ),
    ).toBeInTheDocument();
  });
});
