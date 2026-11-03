import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminUsersPage from './AdminUsersPage';

const mocks = vi.hoisted(() => ({
  adminUsers: vi.fn(),
  suspendUser: vi.fn(),
  restoreUser: vi.fn(),
  activateUser: vi.fn(),
  notify: vi.fn(),
  useAuth: vi.fn(),
}));

vi.mock('../api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api')>();
  return {
    ...actual,
    api: {
      ...actual.api,
      adminUsers: mocks.adminUsers,
      suspendUser: mocks.suspendUser,
      restoreUser: mocks.restoreUser,
      activateUser: mocks.activateUser,
    },
  };
});
vi.mock('../auth/AuthContext', () => ({ useAuth: mocks.useAuth }));
vi.mock('../components/ui/Toast', () => ({ useToast: () => ({ notify: mocks.notify }) }));

const STUDENT = {
  id: 'u-1',
  email: 'student@example.com',
  name: 'Ada',
  role: 'STUDENT' as const,
  status: 'ACTIVE' as const,
  createdAt: '2026-10-01T12:00:00Z',
  studentProfileComplete: true,
};

function pageOf(content: unknown[], totalPages = 1) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/usuarios']}>
      <AdminUsersPage />
    </MemoryRouter>,
  );
}

describe('AdminUsersPage', () => {
  beforeEach(() => {
    mocks.useAuth.mockReturnValue({
      user: { id: 'admin-1', role: 'ADMIN', name: 'Admin' },
      loading: false,
    });
    mocks.suspendUser.mockResolvedValue(undefined);
    mocks.restoreUser.mockResolvedValue(undefined);
    mocks.activateUser.mockResolvedValue(undefined);
  });

  it('lists accounts returned by the API', async () => {
    mocks.adminUsers.mockResolvedValue(pageOf([STUDENT]));

    renderPage();

    expect(await screen.findByText('student@example.com')).toBeInTheDocument();
    expect(screen.getByText('1 cuentas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Suspender' })).toBeEnabled();
  });

  it('asks the API with the selected filters', async () => {
    mocks.adminUsers.mockResolvedValue(pageOf([]));

    renderPage();
    await screen.findByText('Sin cuentas para esos filtros');

    fireEvent.change(screen.getByLabelText('Rol'), { target: { value: 'TEACHER' } });

    await waitFor(() =>
      expect(mocks.adminUsers).toHaveBeenLastCalledWith(
        expect.objectContaining({ role: 'TEACHER', page: 0 }),
      ),
    );
  });

  it('suspends an account and offers undo', async () => {
    mocks.adminUsers.mockResolvedValue(pageOf([STUDENT]));

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Suspender' }));

    await waitFor(() => expect(mocks.suspendUser).toHaveBeenCalledWith('u-1'));
    expect(mocks.notify).toHaveBeenCalledWith(
      'Cuenta suspendida',
      expect.objectContaining({
        action: expect.objectContaining({ label: 'Deshacer' }),
      }),
    );
  });

  it('disables suspension for the signed-in admin', async () => {
    mocks.useAuth.mockReturnValue({
      user: { id: 'u-1', role: 'ADMIN', name: 'Admin' },
      loading: false,
    });
    mocks.adminUsers.mockResolvedValue(pageOf([{ ...STUDENT, role: 'ADMIN' as const }]));

    renderPage();

    expect(await screen.findByRole('button', { name: 'Suspender' })).toBeDisabled();
  });

  it('shows an empty state without results', async () => {
    mocks.adminUsers.mockResolvedValue(pageOf([]));

    renderPage();

    expect(await screen.findByText('Sin cuentas para esos filtros')).toBeInTheDocument();
  });
});
