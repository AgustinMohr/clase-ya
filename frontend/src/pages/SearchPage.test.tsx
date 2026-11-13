import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SearchPage from './SearchPage';

const mocks = vi.hoisted(() => ({
  subjectSearch: vi.fn(),
  teachers: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      subjectSearch: mocks.subjectSearch,
      teachers: mocks.teachers,
    },
  };
});

function renderSearch(entry = '/buscar?materia=Matematica') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SearchPage
        popular={[]}
        loadSuggestions={async () => []}
        subjectsError={false}
        onRetrySubjects={vi.fn()}
        onBack={vi.fn()}
        onOpenTeacher={vi.fn()}
        favorites={new Set()}
      />
    </MemoryRouter>,
  );
}

describe('SearchPage error states', () => {
  beforeEach(() => {
    mocks.subjectSearch.mockReset();
    mocks.teachers.mockReset();
  });

  it('shows a retryable search error when the catalog request fails', async () => {
    mocks.subjectSearch.mockRejectedValue(new Error('network down'));

    renderSearch();

    expect(
      await screen.findByText(
        'No pudimos buscar profesores. Revisá tu conexión e intentá de nuevo.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/No encontramos/)).not.toBeInTheDocument();
    expect(mocks.teachers).not.toHaveBeenCalled();
  });

  it('shows the unmatched-term state only when the catalog confirms no match', async () => {
    mocks.subjectSearch.mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    });

    renderSearch('/buscar?materia=Inexistente');

    expect(await screen.findByText('No encontramos "Inexistente"')).toBeInTheDocument();
    expect(screen.queryByText(/No pudimos buscar profesores/)).not.toBeInTheDocument();
  });

  it('explains invalid filters instead of blaming the connection', async () => {
    const { ApiError } = await import('../api');
    mocks.subjectSearch.mockResolvedValue({
      content: [{ id: 'subject-1', name: 'Matematica' }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });
    mocks.teachers.mockRejectedValue(new ApiError(400, 'Bad Request'));

    renderSearch();

    expect(
      await screen.findByText('Revisá los filtros: hay un valor fuera de rango.'),
    ).toBeInTheDocument();
  });
});
