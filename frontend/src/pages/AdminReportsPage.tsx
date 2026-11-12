import { useCallback, useEffect, useState } from 'react';
import { Check, Flag, X } from 'lucide-react';
import { api, type AdminReport, type Page, type ReportReason, type ReportStatus } from '../api';
import { usePageMeta } from '../hooks/usePageMeta';
import { AdminLayout } from '../components/admin/AdminLayout';
import { Badge, Card, EmptyState, Skeleton } from '../components/ui/primitives';
import { Pagination } from '../components/ui/Pagination';
import { Button } from '../components/ui/Button';
import { Field, Select } from '../components/ui/Field';
import Modal from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';

const STATUS_LABELS: Record<ReportStatus, string> = {
  OPEN: 'Abierto',
  REVIEWING: 'En revisión',
  RESOLVED: 'Resuelto',
  REJECTED: 'Rechazado',
};

const STATUS_TONES: Record<ReportStatus, 'accent' | 'primary' | 'success' | 'secondary'> = {
  OPEN: 'accent',
  REVIEWING: 'primary',
  RESOLVED: 'success',
  REJECTED: 'secondary',
};

const REASON_LABELS: Record<ReportReason, string> = {
  HARASSMENT: 'Acoso o amenazas',
  SPAM: 'Spam o publicidad',
  FAKE_PROFILE: 'Perfil falso o suplantación',
  INAPPROPRIATE: 'Contenido inapropiado',
  OTHER: 'Otro motivo',
};

function formatDate(value?: string | null): string {
  return value ? new Date(value).toLocaleString('es-AR') : '';
}

/** Admin moderation queue for REPORT-001. */
export default function AdminReportsPage() {
  const { notify } = useToast();
  usePageMeta({ title: 'Reportes | ClaseYa' });

  const [status, setStatus] = useState<ReportStatus>('OPEN');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<AdminReport> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [target, setTarget] = useState<{ report: AdminReport; status: 'RESOLVED' | 'REJECTED' } | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await api.adminReports({ status, page, size: 20 }));
    } catch {
      setError('No pudimos cargar los reportes.');
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function resolve() {
    if (!target) return;
    setBusyId(target.report.id);
    try {
      await api.resolveReport(target.report.id, target.status);
      notify(target.status === 'RESOLVED' ? 'Reporte resuelto' : 'Reporte rechazado', 'success');
      setTarget(null);
      void load();
    } catch {
      notify('No pudimos actualizar el reporte.', 'error');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="space-y-6">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Reportes</h1>
          <p className="text-sm text-content-muted">
            Cola de reportes de usuarios. La persona reportada no ve quién la reportó.
          </p>
        </header>

        <Card className="p-4">
          <Field label="Estado">
            {(props) => (
              <Select
                {...props}
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as ReportStatus);
                  setPage(0);
                }}
              >
                <option value="OPEN">Abiertos</option>
                <option value="REVIEWING">En revisión</option>
                <option value="RESOLVED">Resueltos</option>
                <option value="REJECTED">Rechazados</option>
              </Select>
            )}
          </Field>
        </Card>

        {loading ? (
          <div className="space-y-3">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        ) : error ? (
          <EmptyState
            title="Algo salió mal"
            description={error}
            action={<Button onClick={() => void load()}>Reintentar</Button>}
          />
        ) : !data || data.content.length === 0 ? (
          <EmptyState
            title="No hay reportes"
            description="No hay reportes para este estado."
            icon={<Flag className="h-6 w-6" aria-hidden="true" />}
          />
        ) : (
          <>
            <ul className="space-y-3">
              {data.content.map((report) => (
                <li key={report.id}>
                  <Card className="p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold">
                            {report.reporterDisplayName || 'Usuario'} reportó a{' '}
                            {report.reportedDisplayName || 'Usuario'}
                          </span>
                          <Badge tone={STATUS_TONES[report.status]}>
                            {STATUS_LABELS[report.status]}
                          </Badge>
                        </div>
                        <p className="text-sm text-content-muted">
                          {REASON_LABELS[report.reason]} · {formatDate(report.createdAt)}
                        </p>
                        {report.description && (
                          <p className="text-sm text-content-muted">{report.description}</p>
                        )}
                        {report.resolvedAt && (
                          <p className="text-xs text-content-muted">
                            Cerrado: {formatDate(report.resolvedAt)}
                          </p>
                        )}
                      </div>

                      {(report.status === 'OPEN' || report.status === 'REVIEWING') && (
                        <div className="flex shrink-0 gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busyId === report.id}
                            onClick={() => setTarget({ report, status: 'RESOLVED' })}
                          >
                            <Check className="h-4 w-4" aria-hidden="true" /> Resolver
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busyId === report.id}
                            onClick={() => setTarget({ report, status: 'REJECTED' })}
                          >
                            <X className="h-4 w-4" aria-hidden="true" /> Rechazar
                          </Button>
                        </div>
                      )}
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
            <Pagination
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onChange={setPage}
              label="Paginación de reportes"
            />
          </>
        )}
      </div>

      <Modal
        open={target !== null}
        onClose={() => setTarget(null)}
        title={target?.status === 'RESOLVED' ? 'Resolver reporte' : 'Rechazar reporte'}
        size="sm"
      >
        <div className="space-y-4">
          <p className="text-sm text-content-muted">
            {target?.status === 'RESOLVED'
              ? 'El reporte quedará cerrado como resuelto.'
              : 'El reporte quedará cerrado como rechazado.'}{' '}
            Esta acción no se puede deshacer.
          </p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button block loading={busyId !== null} onClick={() => void resolve()}>
              Confirmar
            </Button>
            <Button type="button" variant="outline" block onClick={() => setTarget(null)}>
              Cancelar
            </Button>
          </div>
        </div>
      </Modal>
    </AdminLayout>
  );
}
