import { useEffect, useState } from 'react';
import { api, ApiError, type MyReview, type Review } from '../api';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Select, Textarea } from './ui/Field';

interface Props {
  open: boolean;
  teacherId: string;
  teacherName?: string;
  initial: MyReview | null;
  onClose: () => void;
  onSaved: (review: Review) => void;
}

const RATINGS = [5, 4, 3, 2, 1] as const;

/** Create or edit the current student's review. Eligibility is enforced by the API. */
export default function ReviewModal({
  open,
  teacherId,
  teacherName,
  initial,
  onClose,
  onSaved,
}: Props) {
  const [rating, setRating] = useState<number>(5);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setRating(initial?.rating ?? 5);
    setComment(initial?.comment ?? '');
    setError('');
  }, [open, initial]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError('');
    try {
      const saved = initial
        ? await api.updateReview(initial.id, { rating, comment: comment || undefined })
        : await api.createReview(teacherId, { rating, comment: comment || undefined });
      onSaved(saved);
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 403) {
        setError('Para opinar necesitás un perfil de estudiante y un contacto aceptado: el profesor debe haberte respondido.');
      } else if (caught instanceof ApiError && caught.status === 409) {
        setError('Ya dejaste una opinión sobre este profesor.');
      } else if (caught instanceof ApiError && caught.status === 400) {
        setError('Revisá la calificación y el comentario.');
      } else {
        setError('No pudimos guardar tu opinión. Intentá de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={initial ? 'Editar opinión' : 'Dejar opinión'} size="sm">
      <form className="space-y-4" onSubmit={submit}>
        <p className="text-sm text-content-muted">
          Tu opinión sobre <strong className="text-content">{teacherName || 'este profesor'}</strong>.
        </p>

        <Field label="Calificación">
          {(props) => (
            <Select
              {...props}
              value={rating}
              onChange={(event) => setRating(Number(event.target.value))}
            >
              {RATINGS.map((value) => (
                <option key={value} value={value}>
                  {value} {value === 1 ? 'estrella' : 'estrellas'}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Comentario" hint="Opcional, hasta 1000 caracteres.">
          {(props) => (
            <Textarea
              {...props}
              rows={4}
              maxLength={1000}
              value={comment}
              onChange={(event) => setComment(event.target.value)}
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
            {initial ? 'Guardar cambios' : 'Publicar opinión'}
          </Button>
          <Button type="button" variant="outline" block onClick={onClose}>
            Cancelar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
