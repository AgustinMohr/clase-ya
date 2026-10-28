import { Plus } from 'lucide-react';
import type { AvailabilityWindow } from '../../../api';
import type { AvailabilityForm } from '../../../hooks/useTeacherListing';
import { dayName, modalityLabel } from '../../../lib/format';
import { Button } from '../../ui/Button';
import { Field, Input, Select } from '../../ui/Field';
import { Badge, Card, EmptyState, SubCard } from '../../ui/primitives';

const DAYS = [1, 2, 3, 4, 5, 6, 7];

interface Props {
  availability: AvailabilityWindow[];
  form: AvailabilityForm;
  setForm: React.Dispatch<React.SetStateAction<AvailabilityForm>>;
  addingAvailability: boolean;
  onAdd: () => void | Promise<void>;
  onDisable: (window: AvailabilityWindow) => void | Promise<void>;
}

/** Declared weekly availability. Disabling is reversible (undo), so no confirmation here. */
export default function AvailabilitySection({ availability, form, setForm, addingAvailability, onAdd, onDisable }: Props) {
  return (
    <Card className="p-6">
      <h2 className="mb-4 font-display text-xl">Disponibilidad semanal</h2>
      {availability.length > 0 ? (
        <ul className="mb-4 space-y-2 text-sm">
          {availability.map((window) => (
            <li key={window.id}>
              <SubCard dense className="flex flex-wrap items-center gap-2">
                <span className="w-24 font-semibold">{dayName(window.dayOfWeek)}</span>
                <Badge tone={window.status === 'DISABLED' ? 'neutral' : 'accent'}>
                  {window.startTime}–{window.endTime}
                  {window.mode ? ` · ${modalityLabel(window.mode)}` : ''}
                </Badge>
                {window.status !== 'DISABLED' && (
                  <button type="button" onClick={() => onDisable(window)} className="text-xs font-semibold text-content-muted hover:text-error-600">
                    Desactivar
                  </button>
                )}
              </SubCard>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState size="compact" className="mb-4" title="Todavía no publicaste horarios" />
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Día">
          {({ id }) => (
            <Select id={id} value={form.dayOfWeek} onChange={(e) => setForm({ ...form, dayOfWeek: Number(e.target.value) })}>
              {DAYS.map((day) => (
                <option key={day} value={day}>{dayName(day)}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Desde">
          {({ id }) => <Input id={id} type="time" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />}
        </Field>
        <Field label="Hasta">
          {({ id }) => <Input id={id} type="time" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />}
        </Field>
        <Field label="Modalidad">
          {({ id }) => (
            <Select id={id} value={form.mode} onChange={(e) => setForm({ ...form, mode: e.target.value })}>
              <option value="">Ambas</option>
              <option value="ONLINE">Online</option>
              <option value="IN_PERSON">Presencial</option>
            </Select>
          )}
        </Field>
      </div>
      <div className="mt-4">
        <Button variant="outline" onClick={onAdd} loading={addingAvailability} disabled={!form.startTime || !form.endTime}>
          <Plus className="h-4 w-4" aria-hidden="true" /> Agregar horario
        </Button>
      </div>
    </Card>
  );
}
