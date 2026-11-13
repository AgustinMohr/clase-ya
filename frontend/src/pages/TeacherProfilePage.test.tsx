import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TeacherDetail } from '../api';
import TeacherProfilePage from './TeacherProfilePage';

const mocks = vi.hoisted(() => ({
  teacher: vi.fn(),
  availability: vi.fn(),
  reviews: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      teacher: mocks.teacher,
      availabilityByTeacher: mocks.availability,
      teacherReviews: mocks.reviews,
    },
  };
});

vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: null }),
}));

vi.mock('../hooks/useBack', () => ({
  useBack: () => vi.fn(),
}));

vi.mock('../hooks/usePageMeta', () => ({
  usePageMeta: () => undefined,
  metaDescription: (value?: string) => value,
}));

const teacher: TeacherDetail = {
  id: 'teacher-1',
  displayName: 'Ana',
  bio: 'Profesora de matemática.',
  ratingAverage: 4.8,
  ratingCount: 12,
  verificationStatus: 'VERIFIED',
  pricePerHour: 1500,
  city: 'Santa Fe',
  modalities: ['ONLINE'],
  subjects: [{ subjectId: 'subject-1', subjectName: 'Matemática' }],
  education: [],
};

function page() {
  return { content: [], page: 0, size: 10, totalPages: 0, totalElements: 0 };
}

function renderProfile(teacherId: string) {
  return (
    <MemoryRouter>
      <TeacherProfilePage
        teacherId={teacherId}
        onContact={vi.fn()}
        onToggleFavorite={vi.fn()}
        favorite={false}
      />
    </MemoryRouter>
  );
}

describe('TeacherProfilePage', () => {
  beforeEach(() => {
    mocks.teacher.mockReset();
    mocks.availability.mockReset();
    mocks.reviews.mockReset();
    mocks.availability.mockResolvedValue({ content: [] });
    mocks.reviews.mockResolvedValue(page());
  });

  it('clears an old error when navigating to a valid teacher', async () => {
    mocks.teacher.mockImplementation((id: string) =>
      id === 'missing' ? Promise.reject(new Error('404')) : Promise.resolve(teacher),
    );

    const { rerender } = render(renderProfile('missing'));
    expect(await screen.findByText('No pudimos cargar el perfil.')).toBeInTheDocument();

    rerender(renderProfile('teacher-1'));

    expect(await screen.findByText('Ana')).toBeInTheDocument();
    expect(screen.queryByText('Perfil no disponible')).not.toBeInTheDocument();
  });

  it('retries the same profile after a transient error', async () => {
    mocks.teacher
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValue(teacher);

    render(renderProfile('teacher-1'));
    expect(await screen.findByText('No pudimos cargar el perfil.')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Ana')).toBeInTheDocument();
  });
});
