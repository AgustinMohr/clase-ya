import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import type { OwnEducation } from '../../../api';
import type { EducationForm } from '../../../hooks/useTeacherListing';
import { Button } from '../../ui/Button';
import { Field, Input, Textarea } from '../../ui/Field';
import { Badge, Card, ConfirmDialog, EmptyState, SubCard } from '../../ui/primitives';

interface Props {
  education: OwnEducation[];
  form: EducationForm;
  setForm: React.Dispatch<React.SetStateAction<EducationForm>>;
  addingEducation: boolean;
  onAdd: () => void | Promise<void>;
  onRemove: (item: OwnEducation) => void | Promise<void>;
}

/**
 * Academic background. Removing an item may drop its documents and verification state, so it asks
 * for confirmation instead of offering undo (DESIGN.md §12).
 */
export default function EducationSection({ education, form, setForm, addingEducation, onAdd, onRemove }: Props) {
  const [pending, setPending] = useState<OwnEducation | null>(null);

  return (
    <>
      <Card className="p-6">
        <h2 className="mb-4 font-display text-xl">Formación</h2>
        {education.length > 0 ? (
          <ul className="mb-4 space-y-2 text-sm">
            {education.map((item) => (
              <li key={item.id}>
                <SubCard dense className="flex items-start justify-between gap-3">
                  <div>
                    <span className="font-semibold">{item.degree}</span> — {item.institution}
                    {item.startYear ? ` (${item.startYear}${item.endYear ? `–${item.endYear}` : ''})` : ''}
                    {item.isVerified && <Badge tone="success" className="ml-2">Verificada</Badge>}
                  </div>
                  <button type="button" onClick={() => setPending(item)} aria-label={`Quitar ${item.degree}`} className="text-content-muted hover:text-error-600">
                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                  </button>
                </SubCard>
              </li>
            ))}
          </ul>
        ) : (
          <EmptyState size="compact" className="mb-4" title="Todavía no cargaste tu formación" />
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Institución">
            {({ id }) => <Input id={id} value={form.institution} onChange={(e) => setForm({ ...form, institution: e.target.value })} />}
          </Field>
          <Field label="Título">
            {({ id }) => <Input id={id} value={form.degree} onChange={(e) => setForm({ ...form, degree: e.target.value })} />}
          </Field>
          <Field label="Año de inicio">
            {({ id }) => <Input id={id} type="number" value={form.startYear} onChange={(e) => setForm({ ...form, startYear: e.target.value })} />}
          </Field>
          <Field label="Año de fin">
            {({ id }) => <Input id={id} type="number" value={form.endYear} onChange={(e) => setForm({ ...form, endYear: e.target.value })} />}
          </Field>
        </div>
        <div className="mt-4">
          <Field label="Descripción">
            {({ id }) => <Textarea id={id} rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />}
          </Field>
        </div>
        <div className="mt-4">
          <Button variant="outline" onClick={onAdd} loading={addingEducation} disabled={!form.institution || !form.degree || !form.startYear}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Agregar formación
          </Button>
        </div>
      </Card>

      <ConfirmDialog
        open={pending !== null}
        title="Quitar formación"
        description={pending ? `Se quitará "${pending.degree}" y sus documentos asociados. Esta acción no se puede deshacer.` : undefined}
        confirmLabel="Quitar formación"
        onConfirm={async () => {
          if (pending) await onRemove(pending);
        }}
        onClose={() => setPending(null)}
      />
    </>
  );
}
