import { Plus } from 'lucide-react';
import type {
  AcademicUnit,
  Career,
  CareerSubject,
  OwnSubject,
  University,
} from '../../../api';
import { Button } from '../../ui/Button';
import { Field, Select } from '../../ui/Field';
import { Card, EmptyState, Tag } from '../../ui/primitives';

interface Props {
  subjects: OwnSubject[];
  universities: University[];
  units: AcademicUnit[];
  careers: Career[];
  careerSubjects: CareerSubject[];
  selUniversity: string;
  selUnit: string;
  selCareer: string;
  selCareerSubject: string;
  setSelCareerSubject: (id: string) => void;
  onPickUnit: (id: string) => void;
  onPickCareer: (id: string) => void;
  onPickCareerSubject: (id: string) => void;
  addingSubject: boolean;
  onAddSubject: (careerSubjectId: string) => Promise<boolean>;
  onRemoveSubject: (subject: OwnSubject) => void | Promise<void>;
}

/** Subjects the teacher teaches, picked through the academic cascade. */
export default function SubjectsSection({
  subjects,
  universities,
  units,
  careers,
  careerSubjects,
  selUniversity,
  selUnit,
  selCareer,
  selCareerSubject,
  setSelCareerSubject,
  onPickUnit,
  onPickCareer,
  onPickCareerSubject,
  addingSubject,
  onAddSubject,
  onRemoveSubject,
}: Props) {
  async function add() {
    const added = await onAddSubject(selCareerSubject);
    if (added) setSelCareerSubject('');
  }

  return (
    <Card className="p-6">
      <h2 className="mb-4 font-display text-xl">Materias</h2>
      {subjects.length > 0 ? (
        <ul className="mb-4 flex flex-wrap gap-2">
          {subjects.map((s) => (
            <li key={s.careerSubjectId}>
              <Tag tone="primary" data-testid="subject-tag" onRemove={() => onRemoveSubject(s)} removeLabel={`Quitar ${s.subjectName ?? 'materia'}`}>
                {s.subjectName}
                {s.careerName ? ` · ${s.careerName}` : ''}
              </Tag>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState size="compact" className="mb-4" title="Todavía no agregaste materias" />
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Universidad">
          {({ id }) => (
            <Select id={id} value={selUniversity} onChange={(e) => onPickUnit(e.target.value)}>
              <option value="">Elegir…</option>
              {universities.map((u) => (
                <option key={u.id} value={u.id}>{u.name}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Unidad académica">
          {({ id }) => (
            <Select id={id} value={selUnit} disabled={!selUniversity} onChange={(e) => onPickCareer(e.target.value)}>
              <option value="">Elegir…</option>
              {units.map((u) => (
                <option key={u.id} value={u.id}>{u.name}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Carrera">
          {({ id }) => (
            <Select id={id} value={selCareer} disabled={!selUnit} onChange={(e) => onPickCareerSubject(e.target.value)}>
              <option value="">Elegir…</option>
              {careers.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </Select>
          )}
        </Field>
        <Field label="Materia">
          {({ id }) => (
            <Select id={id} value={selCareerSubject} disabled={!selCareer} onChange={(e) => setSelCareerSubject(e.target.value)}>
              <option value="">Elegir…</option>
              {careerSubjects.map((cs) => (
                <option key={cs.id} value={cs.id}>{cs.subjectName}</option>
              ))}
            </Select>
          )}
        </Field>
      </div>
      <div className="mt-4">
        <Button variant="outline" onClick={add} loading={addingSubject} disabled={!selCareerSubject}>
          <Plus className="h-4 w-4" aria-hidden="true" /> Agregar materia
        </Button>
      </div>
    </Card>
  );
}
