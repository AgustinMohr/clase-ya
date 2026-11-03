import { useCallback, useEffect, useState } from 'react';
import { EyeOff, RotateCcw, Star } from 'lucide-react';
import { api, type AdminReview, type Page, type ReviewStatus } from '../api';
import { usePageMeta } from '../hooks/usePageMeta';
import { AdminLayout } from '../components/admin/AdminLayout';
import { Badge, Card, EmptyState, Skeleton } from '../components/ui/primitives';
import { Pagination } from '../components/ui/Pagination';
import { Button } from '../components/ui/Button';
import { Field, Select, Textarea } from '../components/ui/Field';
import Modal from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';

const STATUS_LABELS: Record<ReviewStatus, string> = {
  PUBLISHED: 'Publicada',
  HIDDEN: 'Oculta',
};

const STATUS_TONES: Record<ReviewStatus, 'success' | 'secondary'> = {
  PUBLISHED: 'success',
  HIDDEN: 'secondary',
};

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleDateString('es-AR') : '';
}

function Stars({ rating }: { rating: number }) {
  return (
    <span className="inline-flex items-center gap-0.5" aria-label={`${rating} de 5 estrellas`}>
      {Array.from({ length: 5 }).map((_, index) => (
        <Star
          key={index}
          className={index < rating ? 'h-3.5 w-3.5 fill-accent-400 text-accent-400' : 'h-3.5 w-3.5 text-content-muted/40'}
          aria-hidden="true"
        />
      ))}
    </span>
  );
}

/** Admin moderation for OPINION-001. */
export default function AdminReviewsPage() {
  const { notify } = useToast();
  usePageMeta({ title: 'Opiniones | ClaseYa' });

  const [status, setStatus] = useState<ReviewStatus | ''>('PUBLISHED');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<AdminReview> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);
  const [hideTarget, setHideTarget] = useState<AdminReview | null>(null);
  const [reason, setReason] = useState('');
  const [hideError, setHideError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setData(await api.adminReviews({ status: status || undefined, page, size: 20 }));
    } catch {
      setError('No pudimos cargar las opiniones.');
    } finally {
      setLoading(false);
    }
  }, [page, status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function confirmHide(event: React.FormEvent) {
    event.preventDefault();
    if (!hideTarget) return;
    if (!reason.trim()) {
      setHideError('El motivo es obligatorio.');
      return;
    }
    setBusyId(hideTarget.id);
    try {
      await api.hideReview(hideTarget.id, reason.trim());
      setHideTarget(null);
      setReason('');
      setHideError('');
      notify('Opinión ocultada', 'success');
      void load();
    } catch {
      setHideError('No pudimos ocultar la opinión.');
    } finally {
      setBusyId(null);
    }
  }

  async function restore(review: AdminReview) {
    setBusyId(review.id);
    try {
      await api.restoreReview(review.id);
      notify('Opinión restaurada', 'success');
      void load();
    } catch {
      notify('No pudimos restaurar la opinión.', 'error');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <AdminLayout>
      <div className="space-y-6">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Opiniones</h1>
          <p className="text-sm text-content-muted">
            Moderación de opiniones de profesores. Ocultar una opinión la saca del perfil y del
            promedio; la auditoría queda registrada.
          </p>
        </header>

        <Card className="p-4">
          <Field label="Estado">
            {(props) => (
              <Select
                {...props}
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as ReviewStatus | '');
                  setPage(0);
                }}
              >
                <option value="">Todas</option>
                <option value="PUBLISHED">Publicadas</option>
                <option value="HIDDEN">Ocultas</option>
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
          <EmptyState title="No hay opiniones" description="No hay opiniones para este filtro." />
        ) : (
          <>
            <ul className="space-y-3">
              {data.content.map((review) => (
                <li key={review.id}>
                  <Card className="p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold">{review.teacherName || 'Profesor'}</span>
                          <Stars rating={review.rating} />
                          <Badge tone={STATUS_TONES[review.status]}>
                            {STATUS_LABELS[review.status]}
                          </Badge>
                        </div>
                        <p className="text-sm text-content-muted">
                          Por {review.authorName || 'Estudiante'} · {formatDate(review.createdAt)}
                        </p>
                        {review.comment && (
                          <p className="text-sm text-content-muted">{review.comment}</p>
                        )}
                        {review.teacherResponse && (
                          <p className="rounded-lg bg-surface-muted px-3 py-2 text-sm text-content-muted">
                            Respuesta: {review.teacherResponse}
                          </p>
                        )}
                        {review.moderationReason && (
                          <p className="text-xs text-content-muted">
                            Motivo de moderación: {review.moderationReason}
                            {review.moderatedByName ? ` · ${review.moderatedByName}` : ''}
                          </p>
                        )}
                      </div>

                      <div className="flex shrink-0 gap-2">
                        {review.status === 'PUBLISHED' ? (
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busyId === review.id}
                            onClick={() => {
                              setHideTarget(review);
                              setReason('');
                              setHideError('');
                            }}
                          >
                            <EyeOff className="h-4 w-4" aria-hidden="true" /> Ocultar
                          </Button>
                        ) : (
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busyId === review.id}
                            onClick={() => void restore(review)}
                          >
                            <RotateCcw className="h-4 w-4" aria-hidden="true" /> Restaurar
                          </Button>
                        )}
                      </div>
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
              label="Paginación de opiniones"
            />
          </>
        )}
      </div>

      <Modal
        open={hideTarget !== null}
        onClose={() => setHideTarget(null)}
        title="Ocultar opinión"
        size="sm"
      >
        <form className="space-y-4" onSubmit={confirmHide}>
          <p className="text-sm text-content-muted">
            La opinión dejará de mostrarse y no contará para el promedio. Indicá el motivo.
          </p>
          <Field label="Motivo" error={hideError}>
            {(props) => (
              <Textarea
                {...props}
                rows={3}
                maxLength={1000}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
              />
            )}
          </Field>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="submit" block loading={busyId !== null}>
              Ocultar
            </Button>
            <Button type="button" variant="outline" block onClick={() => setHideTarget(null)}>
              Cancelar
            </Button>
          </div>
        </form>
      </Modal>
    </AdminLayout>
  );
}
