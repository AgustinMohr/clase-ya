import { useState } from 'react';
import { FileText, Send, Trash2, Upload } from 'lucide-react';
import {
  api,
  type DocumentType,
  type VerificationCredential,
  type VerificationDocument,
  type TeacherVerification,
} from '../../api';
import { useToast } from '../ui/Toast';
import { Badge, Card, ConfirmDialog, EmptyState, Skeleton, SubCard } from '../ui/primitives';
import { Button } from '../ui/Button';
import { Field, Select } from '../ui/Field';

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Pendiente',
  UNDER_REVIEW: 'En revisión',
  VERIFIED: 'Verificado',
  REJECTED: 'Rechazado',
  MORE_INFO_REQUIRED: 'Requiere más información',
};

const DOCUMENT_TYPE_LABEL: Record<DocumentType, string> = {
  DIPLOMA: 'Título / Diploma',
  ENROLLMENT_CERTIFICATE: 'Constancia de título en trámite',
  ANALYTICAL_CERTIFICATE: 'Certificado analítico',
  POSTGRADUATE_CERTIFICATE: 'Certificación de posgrado',
  PROFESSIONAL_LICENSE: 'Matrícula profesional',
  FOREIGN_DEGREE: 'Título del exterior',
};

const DOCUMENT_TYPES = Object.keys(DOCUMENT_TYPE_LABEL) as DocumentType[];

function statusTone(status: string): 'success' | 'accent' | 'neutral' {
  if (status === 'VERIFIED') return 'success';
  if (status === 'UNDER_REVIEW') return 'accent';
  return 'neutral';
}

type ConfirmState = null | {
  title: string;
  description?: string;
  confirmLabel?: string;
  onConfirm: () => void | Promise<void>;
};

function CredentialRow({
  credential,
  onChanged,
}: {
  credential: VerificationCredential;
  onChanged: () => void;
}) {
  const { notify } = useToast();
  const [documents, setDocuments] = useState<VerificationDocument[]>([]);
  const [docsOpen, setDocsOpen] = useState(false);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [type, setType] = useState<DocumentType>('DIPLOMA');
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [confirm, setConfirm] = useState<ConfirmState>(null);

  const verified = credential.status === 'VERIFIED';
  const underReview = credential.status === 'UNDER_REVIEW';
  const canSubmit = credential.documentCount > 0 && !verified && !underReview;

  async function toggleDocs() {
    if (docsOpen) {
      setDocsOpen(false);
      return;
    }
    setDocsOpen(true);
    if (documents.length > 0) return;
    setLoadingDocs(true);
    try {
      setDocuments(await api.myDocuments(credential.educationId));
    } catch {
      notify('No pudimos cargar los documentos.', 'error');
    } finally {
      setLoadingDocs(false);
    }
  }

  async function upload() {
    if (!file) return;
    setUploading(true);
    try {
      await api.uploadDocument(credential.educationId, type, file);
      setFile(null);
      notify('Documento subido');
      onChanged();
      if (docsOpen) setDocuments(await api.myDocuments(credential.educationId));
    } catch {
      notify('No pudimos subir el documento. Solo PDF, JPG o PNG de hasta 10 MB.', 'error');
    } finally {
      setUploading(false);
    }
  }

  async function submit() {
    setSubmitting(true);
    try {
      await api.submitCredential(credential.educationId);
      notify('Credencial presentada a revisión');
      onChanged();
    } catch {
      notify('No pudimos presentar la credencial.', 'error');
    } finally {
      setSubmitting(false);
    }
  }

  /** Deleting a document is not reversible (the file is not retained) → confirmation. */
  function requestRemoveDoc(document: VerificationDocument) {
    setConfirm({
      title: 'Eliminar documento',
      description: `Se eliminará "${document.originalFilename}". Para volver a presentarlo vas a tener que subirlo de nuevo.`,
      confirmLabel: 'Eliminar documento',
      onConfirm: async () => {
        try {
          await api.deleteDocument(document.id);
          setDocuments((prev) => prev.filter((d) => d.id !== document.id));
          notify('Documento eliminado');
          onChanged();
        } catch {
          notify('No pudimos eliminar el documento.', 'error');
        }
      },
    });
  }

  return (
    <SubCard data-testid="credential-row">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-semibold">{credential.degree || 'Formación'}</p>
          {credential.institution && <p className="text-sm text-content-muted">{credential.institution}</p>}
        </div>
        <Badge tone={statusTone(credential.status)}>{STATUS_LABEL[credential.status] ?? credential.status}</Badge>
      </div>

      <p className="mt-2 text-sm text-content-muted">
        {credential.documentCount === 0
          ? 'Sin documentos'
          : `${credential.documentCount} documento${credential.documentCount === 1 ? '' : 's'}`}
        {credential.submittedAt ? ` · presentada` : ''}
      </p>

      {credential.status === 'MORE_INFO_REQUIRED' && credential.requirement && (
        <p className="mt-2 rounded-lg border border-accent-300 bg-accent-50 px-3 py-2 text-sm dark:border-accent-700/30 dark:bg-accent-900/30">
          Te pedimos más información: <span className="font-semibold">{credential.requirement}</span>
        </p>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-3">
        <Field label="Tipo de documento">
          {({ id }) => (
            <Select id={id} value={type} onChange={(e) => setType(e.target.value as DocumentType)}>
              {DOCUMENT_TYPES.map((t) => (
                <option key={t} value={t}>{DOCUMENT_TYPE_LABEL[t]}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Archivo (PDF, JPG o PNG)">
          {({ id }) => (
            <input
              id={id}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="text-sm text-content-muted file:mr-3 file:rounded-lg file:border-0 file:bg-primary-600 file:px-3 file:py-2 file:text-sm file:font-semibold file:text-white hover:file:bg-primary-700"
            />
          )}
        </Field>
        <Button variant="outline" size="sm" onClick={upload} loading={uploading} disabled={!file}>
          <Upload className="h-4 w-4" aria-hidden="true" /> Subir
        </Button>
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <Button size="sm" onClick={submit} loading={submitting} disabled={!canSubmit}>
          <Send className="h-4 w-4" aria-hidden="true" /> Presentar a revisión
        </Button>
        {verified && <p className="text-sm text-content-muted">Credencial verificada.</p>}
        {underReview && <p className="text-sm text-content-muted">Ya está en revisión.</p>}
        {credential.documentCount === 0 && !verified && (
          <p className="text-sm text-content-muted">Subí al menos un documento para presentarla.</p>
        )}
      </div>

      <div className="mt-3">
        <button type="button" onClick={toggleDocs} className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary-600 hover:underline dark:text-primary-200">
          <FileText className="h-4 w-4" aria-hidden="true" />
          {docsOpen ? 'Ocultar documentos' : 'Ver documentos'}
        </button>
        {docsOpen && (
          <div className="mt-2 space-y-1.5">
            {loadingDocs ? (
              <div className="space-y-1.5">
                {Array.from({ length: 2 }).map((_, index) => (
                  <Skeleton key={index} className="h-9" />
                ))}
              </div>
            ) : documents.length === 0 ? (
              <EmptyState size="compact" title="Todavía no hay documentos" />
            ) : (
              documents.map((doc) => (
                <SubCard key={doc.id} dense className="flex items-center justify-between gap-2 bg-surface text-sm">
                  <span className="truncate">
                    {DOCUMENT_TYPE_LABEL[doc.type] ?? doc.type} · {doc.originalFilename}
                  </span>
                  <button type="button" onClick={() => requestRemoveDoc(doc)} aria-label={`Eliminar ${doc.originalFilename}`} className="text-content-muted hover:text-error-600">
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                  </button>
                </SubCard>
              ))
            )}
          </div>
        )}
      </div>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        description={confirm?.description}
        confirmLabel={confirm?.confirmLabel}
        onConfirm={() => confirm?.onConfirm()}
        onClose={() => setConfirm(null)}
      />
    </SubCard>
  );
}

export default function VerificationPanel({
  verification,
  onChanged,
}: {
  verification: TeacherVerification | null;
  onChanged: () => void;
}) {
  const { notify } = useToast();
  const [submittingAll, setSubmittingAll] = useState(false);

  const credentials = verification?.credentials ?? [];
  const hasEligible = credentials.some((c) => c.documentCount > 0 && c.status !== 'VERIFIED' && c.status !== 'UNDER_REVIEW');

  async function submitAll() {
    setSubmittingAll(true);
    try {
      await api.submitAllCredentials();
      notify('Credenciales presentadas a revisión');
      onChanged();
    } catch {
      notify('No pudimos presentar las credenciales.', 'error');
    } finally {
      setSubmittingAll(false);
    }
  }

  return (
    <Card className="p-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-display text-xl">Verificación académica</h2>
        <div className="flex items-center gap-3">
          <Badge tone={verification?.profileStatus === 'VERIFIED' ? 'success' : 'neutral'}>
            {STATUS_LABEL[verification?.profileStatus ?? 'PENDING'] ?? 'Pendiente'}
          </Badge>
          {hasEligible && (
            <Button variant="outline" size="sm" onClick={submitAll} loading={submittingAll}>
              Presentar todas
            </Button>
          )}
        </div>
      </div>

      <p className="mb-4 text-sm text-content-muted">
        Cada credencial de tu formación se verifica por separado. Subí el documento que la respalda y
        presentala: un administrador la revisa y, si la aprueba, tu anuncio muestra el badge de
        verificado.
      </p>

      {credentials.length === 0 ? (
        <EmptyState
          size="compact"
          title="Todavía no tenés formación cargada"
          description='Agregala en la sección "Formación" para poder presentar credenciales.'
        />
      ) : (
        <div className="space-y-3">
          {credentials.map((credential) => (
            <CredentialRow key={credential.educationId} credential={credential} onChanged={onChanged} />
          ))}
        </div>
      )}
    </Card>
  );
}
