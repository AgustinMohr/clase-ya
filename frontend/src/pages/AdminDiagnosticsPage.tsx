import { useState } from 'react';
import { AdminLayout } from '../components/admin/AdminLayout';
import { Button } from '../components/ui/Button';
import { usePageMeta } from '../hooks/usePageMeta';

export const DIAGNOSTICS_ERROR_MESSAGE =
  'Intentional diagnostics error (RT-05): triggered by an admin to verify Sentry';

/**
 * RT-05: lets an ADMIN prove, in a real environment, that a render error reaches Sentry. The error
 * only happens after a click, and it has no effect beyond this browser tab. The route is not linked
 * from the admin navigation. Delete this page once RT-05 is closed.
 */
export default function AdminDiagnosticsPage() {
  usePageMeta({ title: 'Diagnóstico | ClaseYa' });
  const [shouldFail, setShouldFail] = useState(false);

  if (shouldFail) throw new Error(DIAGNOSTICS_ERROR_MESSAGE);

  return (
    <AdminLayout>
      <div className="space-y-4">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Diagnóstico</h1>
          <p className="text-sm text-content-muted">
            Provoca un error de interfaz a propósito para comprobar que llega al monitoreo. Después
            del error, recargá la página.
          </p>
        </header>
        <Button type="button" onClick={() => setShouldFail(true)}>
          Provocar error de prueba
        </Button>
      </div>
    </AdminLayout>
  );
}
