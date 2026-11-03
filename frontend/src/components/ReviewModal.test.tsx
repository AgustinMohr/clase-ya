import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ReviewModal from './ReviewModal';

const mocks = vi.hoisted(() => ({
  createReview: vi.fn(),
  updateReview: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      createReview: mocks.createReview,
      updateReview: mocks.updateReview,
    },
  };
});

const REVIEW = {
  id: 'review-1',
  rating: 4,
  comment: 'Buena clase',
  authorDisplayName: 'Estudiante',
  createdAt: '2026-11-02T12:00:00Z',
};

describe('ReviewModal', () => {
  beforeEach(() => {
    mocks.createReview.mockReset();
    mocks.updateReview.mockReset();
  });

  it('creates a review with the selected rating and comment', async () => {
    mocks.createReview.mockResolvedValue(REVIEW);
    const onSaved = vi.fn();

    render(
      <ReviewModal
        open
        teacherId="teacher-1"
        teacherName="Ana"
        initial={null}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    fireEvent.change(screen.getByLabelText('Calificación'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('Comentario'), { target: { value: 'Buena clase' } });
    fireEvent.click(screen.getByRole('button', { name: 'Publicar opinión' }));

    await waitFor(() =>
      expect(mocks.createReview).toHaveBeenCalledWith('teacher-1', {
        rating: 4,
        comment: 'Buena clase',
      }),
    );
    expect(onSaved).toHaveBeenCalledWith(REVIEW);
  });

  it('explains the accepted-contact rule when the API rejects with 403', async () => {
    const { ApiError } = await import('../api');
    mocks.createReview.mockRejectedValue(new ApiError(403, 'Forbidden'));

    render(
      <ReviewModal
        open
        teacherId="teacher-1"
        teacherName="Ana"
        initial={null}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Publicar opinión' }));

    expect(
      await screen.findByText(
        'Para opinar necesitás un perfil de estudiante y un contacto aceptado: el profesor debe haberte respondido.',
      ),
    ).toBeInTheDocument();
  });
});
