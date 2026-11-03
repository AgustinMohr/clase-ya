import { useCallback, useEffect, useMemo, useState } from 'react';
import { ClipboardCheck, FileText, ShieldAlert } from 'lucide-react';
import {
  api,
  ApiError,
  type AdminCredentialView,
  type AdminVerificationDetail,
  type AdminVerificationDocument,
  type AdminVerificationQueueItem,
  type DecisionInput,
  type Page,
  type VerificationAction,
  type VerificationMethod,
  type VerificationStatus,
} from '../api';
import { usePageMeta } from '../hooks/usePageMeta';
import { AdminLayout } from '../components/admin/AdminLayout';
import { Badge, Card, EmptyState, Skeleton } from '../components/ui/primitives';
import { Pagination } from '../components/ui/Pagination';
import { Button, buttonStyles } from '../components/ui/Button';
import { Field, Select, Textarea } from '../components/ui/Field';
import { useToast } from '../components/ui/Toast';
import { cn } from '../lib/cn';

const STATUS_LABELS: Record<VerificationStatus, string> = {
  PENDING: 'Pendiente',
  UNDER_REVIEW: 'En revisión',
  MORE_INFO_REQUIRED: 'Falta información',
  VERIFIED: 'Verificado',
  REJECTED: 'Rechazado',
};

const STATUS_TONES: Record<VerificationStatus, 'neutral' | 'accent' | 'success' | 'secondary'> = {
  PENDING: 'neutral',
  UNDER_REVIEW: 'accent',
  MORE_INFO_REQUIRED: 'accent',
  VERIFIED: 'success',
  REJECTED: 'secondary',
};

const ACTION_LABELS: Record<VerificationAction, string> = {
  VERIFIED: 'Verificar',
  REJECTED: 'Rechazar',
  MORE_INFO_REQUIRED: 'Pedir más información',
};

const METHOD_LABELS: Record<VerificationMethod, string> = {
  INSTITUTION_CHECK: 'Verificación con la institución',
  OFFICIAL_REGISTRY: 'Registro oficial',
  DIGITAL_DOCUMENT_CHECK: 'Chequeo del documento digital',
  DOCUMENT_ANALYSIS: 'Análisis del documento',
  OTHER: 'Otro',
};

const SCAN_LABELS: Record<string, string> = {
  CLEAN: 'Escaneado',
  UNSCANNED: 'Sin escanear',
  INFECTED: 'Infectado',
  PENDING: 'Escaneo pendiente',
};

const SCAN_TONES: Record<string, 'success' | 'accent' | 'secondary' | 'neutral'> = {
  CLEAN: 'success',
  UNSCANNED: 'accent',
  INFECTED: 'secondary',
  PENDING: 'neutral',
};

const QUEUE_FILTERS: { value: VerificationStatus | ''; label: string }[] = [
  { value: 'UNDER_REVIEW', label: 'En revisión' },
  { value: 'MORE_INFO_REQUIRED', label: 'Falta información' },
  { value: 'VERIFIED', label: 'Verificadas' },
  { value: '', label: 'Todas' },
];

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleDateString('es-AR') : '';
}

function QueueList({
  items,
  selectedTeacherId,
  onSelect,
}: {
  items: AdminVerificationQueueItem[];
  selectedTeacherId: string | null;
  onSelect: (teacherId: string) => void;
}) {
  return (
    <ul className="space-y-2" aria-label="Cola de revisión">
      {items.map((item) => {
        const selected = item.teacherId === selectedTeacherId;
        return (
          <li key={item.educationId}>
            <button
              type="button"
              onClick={() => onSelect(item.teacherId)}
              aria-pressed={selected}
              className={cn(
                'w-full rounded-xl border p-3 text-left transition-colors duration-200',
                selected
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30'
                  : 'border-border bg-surface hover:bg-surface-muted',
              )}
            >
              <span className="flex items-center justify-between gap-2">
                <span className="truncate font-semibold">{item.teacherName?.trim() || 'Profesor'}</span>
                <Badge tone={STATUS_TONES[item.status]}>{STATUS_LABELS[item.status]}</Badge>
              </span>
              <span className="mt-1 block truncate text-sm text-content-muted">
                {[item.degree, item.institution].filter(Boolean).join(' · ')}
              </span>
              <span className="mt-1 block text-xs text-content-muted">
                {item.documentCount} documento{item.documentCount === 1 ? '' : 's'}
                {item.submittedAt ? ` · Presentada el ${formatDate(item.submittedAt)}` : ''}
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

function DocumentRow({
  document,
  busy,
  onToggleReviewed,
}: {
  document: AdminVerificationDocument;
  busy: boolean;
  onToggleReviewed: (document: AdminVerificationDocument, reviewed: boolean) => void;
}) {
  const scan = document.scanStatus ?? 'PENDING';
  const reviewed = Boolean(document.reviewedAt);
  return (
    <li className="flex flex-col gap-2 rounded-xl border border-border bg-surface p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 space-y-1">
        <p className="flex items-center gap-2 truncate font-semibold">
          <FileText className="h-4 w-4 shrink-0 text-content-muted" aria-hidden="true" />
          {document.originalFilename}
        </p>
        <p className="text-xs text-content-muted">
          {document.type} · {formatDate(document.uploadedAt)} · {Math.round(document.sizeBytes / 1024)} KB
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone={SCAN_TONES[scan]}>{SCAN_LABELS[scan]}</Badge>
          {reviewed && (
            <span className="text-xs text-content-muted">
              Revisado{vm(document.reviewedByName)}
            </span>
          )}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <label className="flex min-h-11 items-center gap-2 text-sm font-semibold">
          <input
            type="checkbox"
            className="h-5 w-5 accent-primary-600"
            checked={reviewed}
            disabled={busy}
            onChange={(event) => onToggleReviewed(document, event.target.checked)}
            aria-label={`Marcar ${document.originalFilename} como revisado`}
          />
          Revisado
        </label>
        <a
          href={`/api/admin/documents/${document.id}/content`}
          className={buttonStyles({ variant: 'outline', size: 'sm' })}
        >
          Descargar
        </a>
      </div>
    </li>
  );
}

function vm(name?: string): string {
  return name ? ` por ${name}` : '';
}

function CredentialCard({
  credential,
  documents,
  busyDocumentId,
  onToggleReviewed,
  decisionOpen,
  onOpenDecision,
  decision,
  method,
  reason,
  deciding,
  onDecisionChange,
  onMethodChange,
  onReasonChange,
  onSubmitDecision,
}: {
  credential: AdminCredentialView;
  documents: AdminVerificationDocument[];
  busyDocumentId: string | null;
  onToggleReviewed: (document: AdminVerificationDocument, reviewed: boolean) => void;
  decisionOpen: boolean;
  onOpenDecision: () => void;
  decision: VerificationAction;
  method: VerificationMethod;
  reason: string;
  deciding: boolean;
  onDecisionChange: (value: VerificationAction) => void;
  onMethodChange: (value: VerificationMethod) => void;
  onReasonChange: (value: string) => void;
  onSubmitDecision: () => void;
}) {
  const reviewedCount = documents.filter((document) => document.reviewedAt).length;
  const canVerify = documents.length > 0 && reviewedCount === documents.length;
  const blockedReason = documents.length === 0
    ? 'Necesitás al menos un documento.'
    : `${documents.length - reviewedCount} documento(s) sin revisar.`;
  const submitDisabled = deciding || (decision === 'VERIFIED' && !canVerify)
    || (decision === 'MORE_INFO_REQUIRED' && !reason.trim());

  return (
    <Card className="space-y-4 p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-semibold">{credential.degree || 'Credencial'}</p>
          <p className="truncate text-sm text-content-muted">{credential.institution}</p>
        </div>
        <Badge tone={STATUS_TONES[credential.status]}>{STATUS_LABELS[credential.status]}</Badge>
      </div>

      {credential.requirement && (
        <p className="rounded-xl border border-accent-500/30 bg-accent-50 px-3 py-2 text-sm text-accent-700 dark:bg-accent-700/20 dark:text-accent-300">
          Pedido previo: {credential.requirement}
        </p>
      )}

      {documents.length === 0 ? (
        <p className="text-sm text-content-muted">Sin documentos.</p>
      ) : (
        <>
          <p className="text-xs font-semibold text-content-muted">
            {reviewedCount} de {documents.length} documento(s) revisados
          </p>
          <ul className="space-y-2">
            {documents.map((document) => (
              <DocumentRow
                key={document.id}
                document={document}
                busy={busyDocumentId === document.id}
                onToggleReviewed={onToggleReviewed}
              />
            ))}
          </ul>
        </>
      )}

      {credential.status === 'UNDER_REVIEW' && !decisionOpen && (
        <Button variant="outline" onClick={onOpenDecision}>
          <ClipboardCheck className="h-4 w-4" aria-hidden="true" /> Decidir
        </Button>
      )}

      {credential.status === 'UNDER_REVIEW' && decisionOpen && (
        <form
          className="space-y-3 rounded-xl border border-border bg-surface-muted p-3"
          onSubmit={(event) => {
            event.preventDefault();
            onSubmitDecision();
          }}
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Decisión">
              {(props) => (
                <Select
                  {...props}
                  value={decision}
                  onChange={(event) => onDecisionChange(event.target.value as VerificationAction)}
                >
                  <option value="VERIFIED">Verificar</option>
                  <option value="REJECTED">Rechazar</option>
                  <option value="MORE_INFO_REQUIRED">Pedir más información</option>
                </Select>
              )}
            </Field>
            <Field label="Método">
              {(props) => (
                <Select
                  {...props}
                  value={method}
                  onChange={(event) => onMethodChange(event.target.value as VerificationMethod)}
                >
                  {Object.entries(METHOD_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </Select>
              )}
            </Field>
          </div>
          <Field
            label="Motivo"
            hint={decision === 'MORE_INFO_REQUIRED' ? 'Obligatorio al pedir más información.' : 'Opcional.'}
          >
            {(props) => (
              <Textarea
                {...props}
                value={reason}
                onChange={(event) => onReasonChange(event.target.value)}
                rows={2}
              />
            )}
          </Field>
          {decision === 'VERIFIED' && !canVerify && (
            <p className="text-sm font-semibold text-error-600">{blockedReason}</p>
          )}
          <Button type="submit" disabled={submitDisabled} loading={deciding}>
            Registrar decisión
          </Button>
        </form>
      )}
    </Card>
  );
}

/** Admin review queue (VERIF-001): per-document checkboxes and per-credential decisions. */
export default function AdminVerificationsPage() {
  const { notify } = useToast();
  usePageMeta({ title: 'Verificación | ClaseYa' });

  const [statusFilter, setStatusFilter] = useState<VerificationStatus | ''>('UNDER_REVIEW');
  const [queuePage, setQueuePage] = useState(0);
  const [queueVersion, setQueueVersion] = useState(0);
  const [queue, setQueue] = useState<Page<AdminVerificationQueueItem> | null>(null);
  const [queueLoading, setQueueLoading] = useState(true);
  const [queueError, setQueueError] = useState('');

  const [selectedTeacherId, setSelectedTeacherId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminVerificationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [detailVersion, setDetailVersion] = useState(0);

  const [busyDocumentId, setBusyDocumentId] = useState<string | null>(null);
  const [decisionFor, setDecisionFor] = useState<string | null>(null);
  const [decision, setDecision] = useState<VerificationAction>('VERIFIED');
  const [method, setMethod] = useState<VerificationMethod>('DOCUMENT_ANALYSIS');
  const [reason, setReason] = useState('');
  const [deciding, setDeciding] = useState(false);

  useEffect(() => {
    let active = true;
    setQueueLoading(true);
    setQueueError('');
    api
      .adminVerificationQueue({ status: statusFilter || undefined, page: queuePage, size: 20 })
      .then((result) => {
        if (active) setQueue(result);
      })
      .catch(() => {
        if (active) setQueueError('No pudimos cargar la cola.');
      })
      .finally(() => {
        if (active) setQueueLoading(false);
      });
    return () => {
      active = false;
    };
  }, [statusFilter, queuePage, queueVersion]);

  useEffect(() => {
    if (!selectedTeacherId) {
      setDetail(null);
      return;
    }
    let active = true;
    setDetailLoading(true);
    setDetailError('');
    api
      .adminVerificationDetail(selectedTeacherId)
      .then((result) => {
        if (active) setDetail(result);
      })
      .catch(() => {
        if (active) setDetailError('No pudimos cargar el detalle.');
      })
      .finally(() => {
        if (active) setDetailLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedTeacherId, detailVersion]);

  const documentsByCredential = useMemo(() => {
    const groups = new Map<string, AdminVerificationDocument[]>();
    (detail?.documents ?? []).forEach((document) => {
      const list = groups.get(document.educationId) ?? [];
      list.push(document);
      groups.set(document.educationId, list);
    });
    return groups;
  }, [detail]);

  const toggleReviewed = useCallback(
    async (document: AdminVerificationDocument, reviewed: boolean) => {
      setBusyDocumentId(document.id);
      try {
        await api.reviewDocument(document.id, reviewed);
        setDetailVersion((value) => value + 1);
      } catch {
        notify('No pudimos guardar la revisión.', 'error');
      } finally {
        setBusyDocumentId(null);
      }
    },
    [notify],
  );

  async function submitDecision(credential: AdminCredentialView) {
    if (!selectedTeacherId) return;
    const input: DecisionInput = {
      decision,
      method,
      reason: reason.trim() || undefined,
    };
    setDeciding(true);
    try {
      await api.decideCredential(selectedTeacherId, credential.educationId, input);
      notify('Decisión registrada', 'success');
      setDecisionFor(null);
      setReason('');
      setDetailVersion((value) => value + 1);
      setQueueVersion((value) => value + 1);
    } catch (caught) {
      notify(
        caught instanceof ApiError && caught.status === 400
          ? 'Revisá las condiciones de la decisión (documentos revisados y motivo).'
          : 'No pudimos registrar la decisión.',
        'error',
      );
    } finally {
      setDeciding(false);
    }
  }

  return (
    <AdminLayout>
      <div className="space-y-6">
        <header className="space-y-1">
          <h1 className="font-display text-3xl">Verificación</h1>
          <p className="text-sm text-content-muted">
            Revisá la documentación y decidí por credencial. Verificar exige todos los documentos
            revisados.
          </p>
        </header>

        <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
          <section className="space-y-4" aria-label="Cola">
            <Field label="Estado">
              {(props) => (
                <Select
                  {...props}
                  value={statusFilter}
                  onChange={(event) => {
                    setStatusFilter(event.target.value as VerificationStatus | '');
                    setQueuePage(0);
                  }}
                >
                  {QUEUE_FILTERS.map((filter) => (
                    <option key={filter.label} value={filter.value}>
                      {filter.label}
                    </option>
                  ))}
                </Select>
              )}
            </Field>

            {queueLoading ? (
              <div className="space-y-2" aria-busy="true" aria-label="Cargando cola">
                {Array.from({ length: 3 }).map((_, index) => (
                  <Skeleton key={index} className="h-24" />
                ))}
              </div>
            ) : queueError ? (
              <EmptyState
                icon={<ShieldAlert className="h-6 w-6" aria-hidden="true" />}
                title="No pudimos cargar la cola"
                description="Reintentá en unos segundos."
                action={
                  <Button variant="outline" onClick={() => setQueueVersion((value) => value + 1)}>
                    Reintentar
                  </Button>
                }
              />
            ) : !queue || queue.content.length === 0 ? (
              <EmptyState
                icon={<ClipboardCheck className="h-6 w-6" aria-hidden="true" />}
                title="Nada pendiente"
                description="No hay credenciales con ese estado."
                size="compact"
              />
            ) : (
              <>
                <QueueList
                  items={queue.content}
                  selectedTeacherId={selectedTeacherId}
                  onSelect={setSelectedTeacherId}
                />
                <Pagination
                  page={queue.page}
                  totalPages={queue.totalPages}
                  loading={queueLoading}
                  onChange={setQueuePage}
                  label="Paginación de la cola"
                />
              </>
            )}
          </section>

          <section className="min-w-0 space-y-4" aria-label="Detalle">
            {!selectedTeacherId ? (
              <EmptyState
                icon={<FileText className="h-6 w-6" aria-hidden="true" />}
                title="Elegí una credencial de la cola"
                description="El detalle muestra los documentos, el estado de escaneo y el historial."
              />
            ) : detailLoading ? (
              <div className="space-y-3" aria-busy="true" aria-label="Cargando detalle">
                <Skeleton className="h-24" />
                <Skeleton className="h-64" />
              </div>
            ) : detailError ? (
              <EmptyState
                icon={<ShieldAlert className="h-6 w-6" aria-hidden="true" />}
                title="No pudimos cargar el detalle"
                description="Reintentá en unos segundos."
                action={
                  <Button variant="outline" onClick={() => setDetailVersion((value) => value + 1)}>
                    Reintentar
                  </Button>
                }
              />
            ) : detail ? (
              <>
                <Card className="flex flex-wrap items-center justify-between gap-2 p-4">
                  <div>
                    <p className="font-display text-2xl">{detail.teacherName?.trim() || 'Profesor'}</p>
                    <p className="text-sm text-content-muted">Estado del perfil</p>
                  </div>
                  <Badge tone={STATUS_TONES[detail.profileStatus]}>
                    {STATUS_LABELS[detail.profileStatus]}
                  </Badge>
                </Card>

                {detail.credentials.map((credential) => (
                  <CredentialCard
                    key={credential.educationId}
                    credential={credential}
                    documents={documentsByCredential.get(credential.educationId) ?? []}
                    busyDocumentId={busyDocumentId}
                    onToggleReviewed={toggleReviewed}
                    decisionOpen={decisionFor === credential.educationId}
                    onOpenDecision={() => {
                      setDecisionFor(credential.educationId);
                      setDecision('VERIFIED');
                      setMethod('DOCUMENT_ANALYSIS');
                      setReason('');
                    }}
                    decision={decision}
                    method={method}
                    reason={reason}
                    deciding={deciding}
                    onDecisionChange={setDecision}
                    onMethodChange={setMethod}
                    onReasonChange={setReason}
                    onSubmitDecision={() => submitDecision(credential)}
                  />
                ))}

                <Card className="space-y-3 p-4">
                  <p className="font-semibold">Historial</p>
                  {detail.history.length === 0 ? (
                    <p className="text-sm text-content-muted">Sin decisiones registradas.</p>
                  ) : (
                    <ul className="space-y-2">
                      {detail.history.map((entry) => (
                        <li key={entry.id} className="rounded-xl border border-border p-3 text-sm">
                          <p className="font-semibold">
                            {entry.decision === 'REVOKED'
                              ? 'Verificación revocada'
                              : ACTION_LABELS[entry.decision as VerificationAction]}
                            {entry.newStatus ? ` → ${STATUS_LABELS[entry.newStatus]}` : ''}
                          </p>
                          <p className="text-xs text-content-muted">
                            {formatDate(entry.decidedAt)}
                            {entry.reviewerName ? ` · ${entry.reviewerName}` : ''}
                            {entry.method ? ` · ${METHOD_LABELS[entry.method]}` : ''}
                          </p>
                          {entry.reason && <p className="mt-1 text-content-muted">{entry.reason}</p>}
                        </li>
                      ))}
                    </ul>
                  )}
                </Card>
              </>
            ) : null}
          </section>
        </div>
      </div>
    </AdminLayout>
  );
}
