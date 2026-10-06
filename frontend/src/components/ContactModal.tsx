import { useEffect, useState } from 'react';
import { api, ApiError, MAX_MESSAGE_LENGTH } from '../api';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Input, Select, Textarea } from './ui/Field';

export interface ContactTarget {
  id: string;
  displayName?: string;
  subjects?: { subjectName?: string }[];
}

interface Props {
  open: boolean;
  teacher: ContactTarget | null;
  onClose: () => void;
  /** Called with the conversation id after a successful contact. */
  onSent: (conversationId: string) => void;
  /** Called when the API answers 409: the student has no profile yet. */
  onNeedsProfile: () => void;
}

const SLOTS = ['MAÑANA', 'TARDE', 'NOCHE'];

/**
 * Composes the contact request text (CONTACT-001, D2): subject, preferred slot and
 * role travel inside the first message, so no schema change is needed and the
 * teacher reads something meaningful.
 */
function composeMessage(subject: string, slot: string, role: string, text: string): string {
  const lines = [role === 'PADRE' ? 'Escribo como padre/madre de un estudiante.' : 'Escribo como estudiante.'];
  if (subject.trim()) {
    lines.push(`Materia de interés: ${subject.trim()}.`);
  }
  lines.push(`Franja preferida: ${slot}.`);
  const extra = text.trim();
  if (extra) {
    lines.push('', extra);
  }
  return lines.join('\n');
}

export default function ContactModal({ open, teacher, onClose, onSent, onNeedsProfile }: Props) {
  const subjectNames = [...new Set((teacher?.subjects ?? []).map((s) => s.subjectName).filter(Boolean))] as string[];
  const [subject, setSubject] = useState('');
  const [slot, setSlot] = useState(SLOTS[0]);
  const [role, setRole] = useState('ESTUDIANTE');
  const [text, setText] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setSubject(subjectNames[0] ?? '');
    setSlot(SLOTS[0]);
    setRole('ESTUDIANTE');
    setText('');
    setError('');
    // subjectNames is derived from the teacher, which is fixed while the modal is open.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, teacher?.id]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!teacher) return;
    setLoading(true);
    setError('');
    try {
      const conversation = await api.startConversation(teacher.id, composeMessage(subject, slot, role, text));
      onSent(conversation.id);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        onNeedsProfile();
        return;
      }
      setError(
        e instanceof ApiError && e.status === 404
          ? 'Este perfil ya no está disponible.'
          : 'No pudimos enviar tu mensaje. Intentá de nuevo.',
      );
    } finally {
      setLoading(false);
    }
  }

  // The contact message is composed from role + subject + slot + free text, so the free text can
  // only use what is left of the message budget (extra adds a blank line before it).
  const maxExtra = Math.max(0, MAX_MESSAGE_LENGTH - composeMessage(subject, slot, role, '').length - 2);

  return (
    <Modal open={open} onClose={onClose} title={`Contactar a ${teacher?.displayName || 'el profesor'}`}>
      <form className="space-y-4" onSubmit={submit}>
        <Field label="Materia">
          {(props) =>
            subjectNames.length > 0 ? (
              <Select {...props} value={subject} onChange={(e) => setSubject(e.target.value)} required>
                {subjectNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </Select>
            ) : (
              <Input {...props} maxLength={80} value={subject} onChange={(e) => setSubject(e.target.value)} required />
            )
          }
        </Field>

        <Field label="Franja preferida">
          {(props) => (
            <Select {...props} value={slot} onChange={(e) => setSlot(e.target.value)}>
              {SLOTS.map((option) => (
                <option key={option} value={option}>
                  {option.charAt(0) + option.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Sos">
          {(props) => (
            <Select {...props} value={role} onChange={(e) => setRole(e.target.value)}>
              <option value="ESTUDIANTE">Estudiante</option>
              <option value="PADRE">Padre/madre de un estudiante</option>
            </Select>
          )}
        </Field>

        <Field label="Mensaje" hint="Opcional: contale qué necesitás.">
          {(props) => (
            <Textarea {...props} rows={3} maxLength={maxExtra} value={text} onChange={(e) => setText(e.target.value)} />
          )}
        </Field>

        {error && (
          <p role="alert" className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
            {error}
          </p>
        )}

        <p className="text-xs text-content-muted">
          Tu contacto abre una conversación dentro de ClaseYa: no se comparten tu email ni tu teléfono.
        </p>

        <div className="flex flex-col gap-2 sm:flex-row">
          <Button type="submit" block loading={loading}>
            Enviar mensaje
          </Button>
          <Button type="button" variant="outline" block onClick={onClose}>
            Cancelar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
