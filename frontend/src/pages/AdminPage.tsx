import { LayoutDashboard } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { usePageMeta } from '../hooks/usePageMeta';
import { AdminLayout } from '../components/admin/AdminLayout';
import { EmptyState } from '../components/ui/primitives';

/**
 * Admin shell (ADMIN-001). It deliberately shows no data: the directory and the verification
 * queue land in the next modules, so this page never calls an endpoint.
 */
export default function AdminPage() {
  const { user } = useAuth();
  usePageMeta({ title: 'Administración | ClaseYa' });

  return (
    <AdminLayout>
      <div className="space-y-6">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Administración</h1>
          <p className="text-sm text-content-muted">
            Sesión de {user?.name?.trim() || 'administrador'} (Administrador).
          </p>
        </header>

        <EmptyState
          icon={<LayoutDashboard className="h-6 w-6" aria-hidden="true" />}
          title="Sin módulos todavía"
          description="El directorio de usuarios y la revisión de documentación llegan en los próximos módulos."
        />
      </div>
    </AdminLayout>
  );
}
