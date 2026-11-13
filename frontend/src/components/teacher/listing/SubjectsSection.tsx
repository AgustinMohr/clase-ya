import type { OwnSubject, Subject } from '../../../api';
import SubjectCombobox from '../../subjects/SubjectCombobox';
import { Field } from '../../ui/Field';
import { Card, EmptyState, Tag } from '../../ui/primitives';

interface Props {
  subjects: OwnSubject[];
  addingSubject: boolean;
  searchSubjects: (term: string) => Promise<Subject[]>;
  onAddSubjectById: (subjectId: string) => Promise<boolean>;
  onRemoveSubject: (subject: OwnSubject) => void | Promise<void>;
}

/**
 * PROFILE-004: a teacher publishes subjects directly from the catalog. The old
 * university/faculty/career cascade is gone; a school tutor can add “Matemática” or “Inglés”
 * without inventing academic data.
 */
export default function SubjectsSection({
  subjects,
  addingSubject,
  searchSubjects,
  onAddSubjectById,
  onRemoveSubject,
}: Props) {
  return (
    <Card className="p-6">
      <h2 className="mb-4 font-display text-xl">Materias</h2>
      {subjects.length > 0 ? (
        <ul className="mb-4 flex flex-wrap gap-2">
          {subjects.map((subject) => (
            <li key={subject.id}>
              <Tag
                tone="primary"
                data-testid="subject-tag"
                onRemove={() => onRemoveSubject(subject)}
                removeLabel={`Quitar ${subject.subjectName ?? 'materia'}`}
              >
                {subject.subjectName}
                {subject.careerName ? ` · ${subject.careerName}` : ''}
              </Tag>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState size="compact" className="mb-4" title="Todavía no agregaste materias" />
      )}

      <Field label="Buscar materia" hint="Elegí la materia que enseñás; no hace falta una carrera.">
        {({ id }) => (
          <SubjectCombobox
            id={id}
            loadSubjects={searchSubjects}
            onSelect={async (subject) => {
              await onAddSubjectById(subject.id);
            }}
            disabled={addingSubject}
          />
        )}
      </Field>
    </Card>
  );
}
