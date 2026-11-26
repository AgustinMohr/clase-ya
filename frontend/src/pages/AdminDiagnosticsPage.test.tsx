import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { ProtectedRoute } from '../components/routing/ProtectedRoute';
import { useAuth } from '../auth/AuthContext';
import { reportRenderError } from '../observability/sentry';
import type { SessionUser } from '../api';
import AdminDiagnosticsPage, { DIAGNOSTICS_ERROR_MESSAGE } from './AdminDiagnosticsPage';

vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../observability/sentry', () => ({ reportRenderError: vi.fn() }));

const mockedUseAuth = vi.mocked(useAuth);

function renderAs(role: SessionUser['role']) {
  mockedUseAuth.mockReturnValue({
    user: { id: '1', name: 'Ada', role },
    loading: false,
  } as unknown as ReturnType<typeof useAuth>);

  return render(
    <MemoryRouter initialEntries={['/admin/diagnostico']}>
      <ErrorBoundary>
        <ProtectedRoute role="ADMIN" onRequireLogin={vi.fn()}>
          <AdminDiagnosticsPage />
        </ProtectedRoute>
      </ErrorBoundary>
    </MemoryRouter>,
  );
}

describe('AdminDiagnosticsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(reportRenderError).mockClear();
  });

  it('does not fail until the admin asks for it', () => {
    renderAs('ADMIN');

    expect(screen.getByRole('button', { name: 'Provocar error de prueba' })).toBeInTheDocument();
    expect(reportRenderError).not.toHaveBeenCalled();
  });

  it('breaks the render on click and reports the error', () => {
    // React logs the caught error; keep the test output clean.
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    renderAs('ADMIN');

    fireEvent.click(screen.getByRole('button', { name: 'Provocar error de prueba' }));

    expect(screen.getByRole('heading', { name: 'Algo salió mal' })).toBeInTheDocument();
    expect(reportRenderError).toHaveBeenCalledTimes(1);
    expect(vi.mocked(reportRenderError).mock.calls[0][0].message).toBe(DIAGNOSTICS_ERROR_MESSAGE);
  });

  it('is closed to non-admins', () => {
    renderAs('STUDENT');

    expect(screen.getByText(/No tenés acceso/)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Provocar error de prueba' }),
    ).not.toBeInTheDocument();
  });
});
