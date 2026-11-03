import { useEffect, useState } from 'react';
import { Search, UserRound, Users } from 'lucide-react';
import {
  api,
  ApiError,
  type AccountRole,
  type AccountStatus,
  type AdminUserSummary,
  type Page,
  type VerificationStatus,
} from '../api';
import { useAuth } from '../auth/AuthContext';
import { usePageMeta } from '../hooks/usePageMeta';
import { AdminLayout } from '../components/admin/AdminLayout';
import { Badge, Card, EmptyState, Skeleton } from '../components/ui/primitives';
import { Pagination } from '../components/ui/Pagination';
import { Button } from '../components/ui/Button';
import { Field, Input, Select } from '../components/ui/Field';
import { useToast } from '../components/ui/Toast';

const ROLE_LABELS: Record<AccountRole, string> = {
  STUDENT: 'Estudiante',
  TEACHER: 'Profesor',
  ADMIN: 'Administrador',
};

const ROLE_TONES: Record<AccountRole, 'neutral' | 'primary' | 'accent'> = {
  STUDENT: 'neutral',
  TEACHER: 'primary',
  ADMIN: 'accent',
};

const STATUS_LABELS: Record<AccountStatus, string> = {
  ACTIVE: 'Activa',
  PENDING: 'Pendiente',
  SUSPENDED: 'Suspendida',
  INACTIVE: 'Inactiva',
};

const STATUS_TONES: Record<AccountStatus, 'success' | 'accent' | 'secondary' | 'neutral'> = {
  ACTIVE: 'success',
  PENDING: 'accent',
  SUSPENDED: 'secondary',
  INACTIVE: 'neutral',
};

const VERIFICATION_LABELS: Record<VerificationStatus, string> = {
  PENDING: 'Verificación pendiente',
  UNDER_REVIEW: 'En revisión',
  MORE_INFO_REQUIRED: 'Falta información',
  VERIFIED: 'Verificado',
  REJECTED: 'Rechazado',
};

const VERIFICATION_TONES: Record<VerificationStatus, 'neutral' | 'accent' | 'success' | 'secondary'> = {
  PENDING: 'neutral',
  UNDER_REVIEW: 'accent',
  MORE_INFO_REQUIRED: 'accent',
  VERIFIED: 'success',
  REJECTED: 'secondary',
};

const PAGE_SIZE = 20;

function formatDate(value?: string): string {
  if (!value) return '';
  return new Date(value).toLocaleDateString('es-AR');
}

type UserAction = 'suspend' | 'restore' | 'activate';

function UserRow({
  user,
  isSelf,
  busy,
  onAction,
}: {
  user: AdminUserSummary;
  isSelf: boolean;
  busy: boolean;
  onAction: (user: AdminUserSummary, action: UserAction) => void;
}) {
  return (
    <Card className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate font-semibold">{user.name?.trim() || user.email}</p>
          <Badge tone={ROLE_TONES[user.role]}>{ROLE_LABELS[user.role]}</Badge>
          <Badge tone={STATUS_TONES[user.status]}>{STATUS_LABELS[user.status]}</Badge>
          {user.role === 'TEACHER' && user.teacherVerificationStatus && (
            <Badge tone={VERIFICATION_TONES[user.teacherVerificationStatus]}>
              {VERIFICATION_LABELS[user.teacherVerificationStatus]}
            </Badge>
          )}
          {user.role === 'STUDENT' && user.studentProfileComplete === false && (
            <Badge tone="neutral">Sin perfil</Badge>
          )}
        </div>
        <p className="truncate text-sm text-content-muted">{user.email}</p>
        <p className="text-xs text-content-muted">
          Alta: {formatDate(user.createdAt)}
          {user.lastLoginAt ? ` · Último ingreso: ${formatDate(user.lastLoginAt)}` : ''}
        </p>
      </div>

      <div className="flex shrink-0 gap-2">
        {user.status === 'PENDING' && (
          <Button variant="outline" size="sm" disabled={busy} onClick={() => onAction(user, 'activate')}>
            Activar
          </Button>
        )}
        {user.status === 'ACTIVE' && (
          <Button
            variant="outline"
            size="sm"
            disabled={busy || isSelf}
            title={isSelf ? 'No podés suspender tu propia cuenta' : undefined}
            onClick={() => onAction(user, 'suspend')}
          >
            Suspender
          </Button>
        )}
        {user.status === 'SUSPENDED' && (
          <Button variant="outline" size="sm" disabled={busy} onClick={() => onAction(user, 'restore')}>
            Restaurar
          </Button>
        )}
      </div>
    </Card>
  );
}

/** Admin user directory (ADMIN-002): filters, paginated rows and account-status actions. */
export default function AdminUsersPage() {
  const { user: sessionUser } = useAuth();
  const { notify } = useToast();
  usePageMeta({ title: 'Usuarios | ClaseYa' });

  const [role, setRole] = useState<AccountRole | ''>('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [term, setTerm] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [data, setData] = useState<Page<AdminUserSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api
      .adminUsers({ role: role || null, status: status || null, query, page, size: PAGE_SIZE })
      .then((result) => {
        if (active) setData(result);
      })
      .catch(() => {
        if (active) setError('No pudimos cargar el directorio.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [role, status, query, page, version]);

  function applyFilters(event: React.FormEvent) {
    event.preventDefault();
    setQuery(term.trim());
    setPage(0);
  }

  async function runAction(target: AdminUserSummary, action: UserAction) {
    setBusyId(target.id);
    try {
      if (action === 'suspend') {
        await api.suspendUser(target.id);
        notify('Cuenta suspendida', {
          tone: 'success',
          action: {
            label: 'Deshacer',
            onClick: async () => {
              await api.restoreUser(target.id);
              setVersion((value) => value + 1);
              notify('Cuenta restaurada');
            },
          },
        });
      } else if (action === 'restore') {
        await api.restoreUser(target.id);
        notify('Cuenta restaurada', 'success');
      } else {
        await api.activateUser(target.id);
        notify('Cuenta activada', 'success');
      }
      setVersion((value) => value + 1);
    } catch (caught) {
      notify(
        caught instanceof ApiError && caught.status === 409
          ? caught.message
          : 'No pudimos actualizar la cuenta.',
        'error',
      );
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="space-y-6">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Usuarios</h1>
          <p className="text-sm text-content-muted">
            {data ? `${data.totalElements} cuentas` : 'Directorio de cuentas'}
          </p>
        </header>

        <Card className="p-4">
          <form className="grid gap-3 lg:grid-cols-[1fr_190px_190px_auto]" onSubmit={applyFilters}>
            <Field label="Buscar">
              {(props) => (
                <Input
                  {...props}
                  type="search"
                  value={term}
                  onChange={(event) => setTerm(event.target.value)}
                  placeholder="Email o nombre"
                  autoComplete="off"
                />
              )}
            </Field>
            <Field label="Rol">
              {(props) => (
                <Select
                  {...props}
                  value={role}
                  onChange={(event) => {
                    setRole(event.target.value as AccountRole | '');
                    setPage(0);
                  }}
                >
                  <option value="">Todos</option>
                  <option value="STUDENT">Estudiantes</option>
                  <option value="TEACHER">Profesores</option>
                  <option value="ADMIN">Administradores</option>
                </Select>
              )}
            </Field>
            <Field label="Estado">
              {(props) => (
                <Select
                  {...props}
                  value={status}
                  onChange={(event) => {
                    setStatus(event.target.value as AccountStatus | '');
                    setPage(0);
                  }}
                >
                  <option value="">Todos</option>
                  <option value="ACTIVE">Activas</option>
                  <option value="PENDING">Pendientes</option>
                  <option value="SUSPENDED">Suspendidas</option>
                  <option value="INACTIVE">Inactivas</option>
                </Select>
              )}
            </Field>
            <div className="flex items-end">
              <Button type="submit" block>
                <Search className="h-4 w-4" aria-hidden="true" /> Buscar
              </Button>
            </div>
          </form>
        </Card>

        {loading ? (
          <div className="space-y-3" aria-busy="true" aria-label="Cargando usuarios">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-24" />
            ))}
          </div>
        ) : error ? (
          <EmptyState
            icon={<Users className="h-6 w-6" aria-hidden="true" />}
            title="No pudimos cargar el directorio"
            description="Revisá la conexión e intentá de nuevo."
            action={
              <Button variant="outline" onClick={() => setVersion((value) => value + 1)}>
                Reintentar
              </Button>
            }
          />
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            icon={<UserRound className="h-6 w-6" aria-hidden="true" />}
            title="Sin cuentas para esos filtros"
            description="Probá con otro rol, estado o término de búsqueda."
          />
        ) : (
          <>
            <ul className="space-y-3" aria-label="Cuentas">
              {data.content.map((user) => (
                <li key={user.id}>
                  <UserRow
                    user={user}
                    isSelf={user.id === sessionUser?.id}
                    busy={busyId === user.id}
                    onAction={runAction}
                  />
                </li>
              ))}
            </ul>
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              loading={loading}
              onChange={setPage}
              label="Paginación de usuarios"
            />
          </>
        )}
      </div>
    </AdminLayout>
  );
}
