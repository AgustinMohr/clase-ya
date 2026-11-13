import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { useAuth } from '../../auth/AuthContext';
import type { SessionUser } from '../../api';
import { Navbar } from './Navbar';

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }));
vi.mock('../../theme/ThemeProvider', () => ({
  useTheme: () => ({ theme: 'light', toggle: vi.fn() }),
}));

const mockedUseAuth = vi.mocked(useAuth);

function session(user: SessionUser | null) {
  return {
    user,
    loading: false,
    logout: vi.fn(),
  } as unknown as ReturnType<typeof useAuth>;
}

function renderNavbar() {
  return render(
    <MemoryRouter>
      <Navbar onProfile={vi.fn()} onLogin={vi.fn()} />
    </MemoryRouter>,
  );
}

describe('Navbar admin entry', () => {
  it('shows the Admin link to an admin session', () => {
    mockedUseAuth.mockReturnValue(session({ id: '1', name: 'Ada', role: 'ADMIN' }));

    renderNavbar();

    expect(screen.getByRole('link', { name: 'Admin' })).toHaveAttribute('href', '/admin');
  });

  it('does not render the Admin link for students', () => {
    mockedUseAuth.mockReturnValue(session({ id: '2', name: 'Bob', role: 'STUDENT' }));

    renderNavbar();

    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });

  it('does not render the Admin link for anonymous visitors', () => {
    mockedUseAuth.mockReturnValue(session(null));

    renderNavbar();

    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
  });

  it('keeps accessible names for icon-only controls on mobile', () => {
    mockedUseAuth.mockReturnValue(session({ id: '3', name: 'Ana', role: 'STUDENT' }));

    renderNavbar();

    expect(screen.getByRole('link', { name: 'Mensajes' })).toHaveAttribute('aria-label', 'Mensajes');
    expect(screen.getByRole('link', { name: 'Favoritos' })).toHaveAttribute('aria-label', 'Favoritos');
    expect(screen.getByRole('button', { name: 'Mi perfil' })).toHaveAttribute('aria-label', 'Mi perfil');
    expect(screen.getByRole('button', { name: 'Salir' })).toHaveAttribute('aria-label', 'Salir');
  });
});
