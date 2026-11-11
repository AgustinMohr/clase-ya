import { useState } from 'react';
import { Plus, Search } from 'lucide-react';
import type { OwnSubject, Subject } from '../../../api';
import { Button } from '../../ui/Button';
import { Field, Input, Select } from '../../ui/Field';
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
  const [term, setTerm] = useState('');
  const [results, setResults] = useState<Subject[]>([]);
  const [selected, setSelected] = useState('');
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState('');

  async function search(event: React.FormEvent) {
    event.preventDefault();
    const query = term.trim();
    if (!query) return;
    setSearching(true);
    setError('');
    try {
      const list = await searchSubjects(query);
      setResults(list);
      setSelected(list[0]?.id ?? '');
      if (list.length === 0) {
        setError('No encontramos materias con ese nombre. Probá con otra palabra.');
      }
    } catch {
      setError('No pudimos buscar materias. Revisá tu conexión e intentá de nuevo.');
    } finally {
      setSearching(false);
    }
  }

  async function add() {
    if (!selected) return;
    const added = await onAddSubjectById(selected);
    if (added) {
      setTerm('');
      setResults([]);
      setSelected('');
      setError('');
    }
  }

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

      <form onSubmit={search} className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
        <Field label="Buscar materia" hint="Elegí la materia que enseñás; no hace falta una carrera.">
          {(props) => (
            <Input
              {...props}
              type="search"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Ej. Matemática, Inglés, Apoyo escolar"
              maxLength={255}
            />
          )}
        </Field>
        <Button type="submit" variant="outline" loading={searching} disabled={!term.trim()}>
          <Search className="h-4 w-4" aria-hidden="true" /> Buscar
        </Button>
      </form>

      {results.length > 0 && (
        <div className="mt-4 grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
          <Field label="Resultados">
            {(props) => (
              <Select
                {...props}
                value={selected}
                onChange={(e) => setSelected(e.target.value)}
              >
                {results.map((subject) => (
                  <option key={subject.id} value={subject.id}>
                    {subject.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>
          <Button onClick={() => void add()} loading={addingSubject} disabled={!selected}>
            <Plus className="h-4 w-4" aria-hidden="true" /> Agregar materia
          </Button>
        </div>
      )}

      {error && (
        <p role="alert" className="mt-4 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
          {error}
        </p>
      )}
    </Card>
  );
}
