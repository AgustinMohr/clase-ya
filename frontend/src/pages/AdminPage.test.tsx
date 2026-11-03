import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { ProtectedRoute } from '../components/routing/ProtectedRoute';
import { useAuth } from '../auth/AuthContext';
import type { SessionUser } from '../api';
import AdminPage from './AdminPage';

vi.mock('../auth/AuthContext', () => ({ useAuth: vi.fn() }));

const mockedUseAuth = vi.mocked(useAuth);

function session(user: SessionUser | null) {
  return {
    user,
    loading: false,
    login: vi.fn(),
    loginWithGoogle: vi.fn(),
    register: vi.fn(),
    activate: vi.fn(),
    resendActivation: vi.fn(),
    logout: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>;
}

function renderAdmin() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <ProtectedRoute role="ADMIN" onRequireLogin={vi.fn()}>
        <AdminPage />
      </ProtectedRoute>
    </MemoryRouter>,
  );
}

describe('AdminPage', () => {
  it('renders the shell for an admin session', () => {
    mockedUseAuth.mockReturnValue(session({ id: '1', name: 'Ada', role: 'ADMIN' }));

    renderAdmin();

    expect(screen.getByRole('heading', { name: 'Administración' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Administración' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Resumen' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText(/Sesión de Ada/)).toBeInTheDocument();
  });

  it('does not render the panel for a non-admin session', () => {
    mockedUseAuth.mockReturnValue(session({ id: '2', name: 'Bob', role: 'STUDENT' }));

    renderAdmin();

    expect(screen.getByText(/No tenés acceso/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Administración' })).not.toBeInTheDocument();
  });
});
