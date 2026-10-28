import { useEffect } from 'react';
import { Link } from 'react-router';
import { Lock, ShieldAlert } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { usePageMeta } from '../../hooks/usePageMeta';
import { buttonStyles } from '../ui/Button';
import { EmptyState, Skeleton } from '../ui/primitives';
import { ROUTES } from '../../routes';

interface Props {
  role?: 'STUDENT' | 'TEACHER';
  onRequireLogin: () => void;
  children: React.ReactNode;
}

/**
 * UX guard for private routes. The API is still the authority: this only decides what to
 * render before making requests that would end in 401/403.
 */
export function ProtectedRoute({ role, onRequireLogin, children }: Props) {
  const { user, loading } = useAuth();

  // The session lives in an HttpOnly cookie, so the role is only known after /api/auth/me.
  if (loading) return <SessionLoadingGate />;
  if (!user) return <SignInGate onRequireLogin={onRequireLogin} />;
  if (role && user.role !== role) return <AccessDeniedGate />;
  return <>{children}</>;
}

function SessionLoadingGate() {
  usePageMeta({ title: 'Cargando sesión | ClaseYa' });

  return (
    <div className="container-page space-y-5 py-8" aria-busy="true" aria-label="Cargando sesión">
      <Skeleton className="h-24" />
      <Skeleton className="h-64" />
    </div>
  );
}

function SignInGate({ onRequireLogin }: { onRequireLogin: () => void }) {
  usePageMeta({ title: 'Iniciar sesión | ClaseYa' });

  useEffect(() => {
    onRequireLogin();
  }, [onRequireLogin]);

  return (
    <div className="container-page py-10">
      <EmptyState
        icon={<Lock className="h-6 w-6" aria-hidden="true" />}
        title="Iniciá sesión para continuar"
        description="Esta sección es privada. Ingresá con tu cuenta para verla."
        action={
          <button type="button" onClick={onRequireLogin} className={buttonStyles({ size: 'md' })}>
            Ingresar
          </button>
        }
      />
    </div>
  );
}

function AccessDeniedGate() {
  usePageMeta({ title: 'Acceso restringido | ClaseYa' });

  return (
    <div className="container-page py-10">
      <EmptyState
        icon={<ShieldAlert className="h-6 w-6" aria-hidden="true" />}
        title="No tenés acceso a esta sección"
        description="Tu cuenta no tiene el rol necesario para ver esta página."
        action={
          <Link to={ROUTES.home} className={buttonStyles({ variant: 'outline', size: 'md' })}>
            Volver al inicio
          </Link>
        }
      />
    </div>
  );
}
