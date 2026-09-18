import { useState } from 'react';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Input, Select, Textarea } from './ui/Field';

interface Props {
  open: boolean;
  teacherName: string;
  onClose: () => void;
}

const STORAGE_KEY = 'claseya.contactRequests';

/**
 * Prototype-only contact. There is no CONTACT-001 backend yet; the request is
 * stored locally and clearly flagged as simulated.
 */
export default function ContactModal({ open, teacherName, onClose }: Props) {
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [slot, setSlot] = useState('MAÑANA');
  const [who, setWho] = useState('estudiante');
  const [sent, setSent] = useState(false);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const requests = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? '[]');
    requests.push({ teacherName, subject, message, slot, who, at: new Date().toISOString() });
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(requests));
    setSent(true);
  }

  return (
    <Modal open={open} onClose={onClose} title={`Contactar a ${teacherName || 'el profesor'}`}>
      {sent ? (
        <div className="space-y-4">
          <p className="font-semibold text-success-700 dark:text-success-500">Solicitud registrada (simulada).</p>
          <p className="text-sm text-content-muted">
            Prototipo: el envío real (email del sistema + mensajería interna) llega con CONTACT-001.
          </p>
          <Button block onClick={onClose}>
            Cerrar
          </Button>
        </div>
      ) : (
        <form className="space-y-4" onSubmit={submit}>
          <Field label="Materia">
            {(props) => <Input {...props} value={subject} onChange={(e) => setSubject(e.target.value)} required />}
          </Field>
          <Field label="Mensaje" hint="Contale qué necesitás (nivel, objetivo, horarios).">
            {(props) => <Textarea {...props} rows={3} value={message} onChange={(e) => setMessage(e.target.value)} />}
          </Field>
          <Field label="Franja preferida">
            {(props) => (
              <Select {...props} value={slot} onChange={(e) => setSlot(e.target.value)}>
                <option>MAÑANA</option>
                <option>TARDE</option>
                <option>NOCHE</option>
              </Select>
            )}
          </Field>
          <Field label="Sos">
            {(props) => (
              <Select {...props} value={who} onChange={(e) => setWho(e.target.value)}>
                <option value="estudiante">Estudiante</option>
                <option value="padre">Padre/madre de un estudiante</option>
              </Select>
            )}
          </Field>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Button type="submit" block>
              Enviar solicitud
            </Button>
            <Button type="button" variant="outline" block onClick={onClose}>
              Cancelar
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
