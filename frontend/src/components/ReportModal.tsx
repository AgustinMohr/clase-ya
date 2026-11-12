import { useEffect, useState } from 'react';
import { api, ApiError, type Report, type ReportReason } from '../api';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Select, Textarea } from './ui/Field';

interface Props {
  open: boolean;
  reportedUserId: string;
  reportedName?: string;
  onClose: () => void;
  onReported: (report: Report) => void;
}

const REASONS: { value: ReportReason; label: string }[] = [
  { value: 'HARASSMENT', label: 'Acoso o amenazas' },
  { value: 'SPAM', label: 'Spam o publicidad' },
  { value: 'FAKE_PROFILE', label: 'Perfil falso o suplantación' },
  { value: 'INAPPROPRIATE', label: 'Contenido inapropiado' },
  { value: 'OTHER', label: 'Otro motivo' },
];

/** REPORT-001: report the other participant of a conversation. */
export default function ReportModal({
  open,
  reportedUserId,
  reportedName,
  onClose,
  onReported,
}: Props) {
  const [reason, setReason] = useState<ReportReason>('HARASSMENT');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setReason('HARASSMENT');
    setDescription('');
    setError('');
  }, [open]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const report = await api.reportUser(reportedUserId, reason, description.trim() || undefined);
      onReported(report);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setError('Ya reportaste a este usuario. Administración está revisando el caso.');
      } else if (caught instanceof ApiError && caught.status === 400) {
        setError('Revisá el motivo antes de enviar el reporte.');
      } else {
        setError('No pudimos enviar el reporte. Intentá de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Reportar usuario" size="sm">
      <form className="space-y-4" onSubmit={submit}>
        <p className="text-sm text-content-muted">
          Reportás a <strong className="text-content">{reportedName || 'este usuario'}</strong>.
          Administración revisa el reporte; la otra persona no ve quién lo envió.
        </p>

        <Field label="Motivo">
          {(props) => (
            <Select
              {...props}
              value={reason}
              onChange={(event) => setReason(event.target.value as ReportReason)}
            >
              {REASONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Detalle" hint="Opcional, hasta 1000 caracteres.">
          {(props) => (
            <Textarea
              {...props}
              rows={4}
              maxLength={1000}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          )}
        </Field>

        {error && (
          <p role="alert" className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
            {error}
          </p>
        )}

        <div className="flex flex-col gap-2 sm:flex-row">
          <Button type="submit" block loading={loading}>
            Enviar reporte
          </Button>
          <Button type="button" variant="outline" block onClick={onClose}>
            Cancelar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
