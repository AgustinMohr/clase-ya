import { useEffect, useState } from 'react';
import {
  api,
  ApiError,
  MAX_STUDENT_BIO_LENGTH,
  type AcademicUnit,
  type Career,
  type StudentLevel,
  type StudentProfile,
  type StudentProfileInput,
  type University,
} from '../api';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Input, Select, Textarea } from './ui/Field';

interface Props {
  open: boolean;
  onClose: () => void;
  /** Called after a successful create/update. */
  onSaved: (profile: StudentProfile) => void;
  /** Existing profile when editing; null when the student still has none. */
  initial: StudentProfile | null;
}

const LEVELS: Array<{ value: StudentLevel; label: string }> = [
  { value: 'PRIMARIA', label: 'Primaria' },
  { value: 'SECUNDARIA', label: 'Secundaria' },
  { value: 'TERCIARIO', label: 'Terciario' },
  { value: 'UNIVERSIDAD', label: 'Universidad' },
  { value: 'ADULTO', label: 'Adulto / aprendizaje libre' },
];

function levelLabel(level: StudentLevel | null | undefined): string {
  return LEVELS.find((option) => option.value === level)?.label ?? 'Sin nivel';
}

/**
 * Student profile form (CONTACT-001 / PROFILE-003). Name and level are the minimum; the
 * university/career cascade is optional so primary, secondary and adult learners can use the
 * product without inventing academic data.
 */
export default function StudentProfileModal({ open, onClose, onSaved, initial }: Props) {
  const [universities, setUniversities] = useState<University[]>([]);
  const [units, setUnits] = useState<AcademicUnit[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [name, setName] = useState('');
  const [level, setLevel] = useState<StudentLevel | ''>('');
  const [city, setCity] = useState('');
  const [universityId, setUniversityId] = useState('');
  const [unitId, setUnitId] = useState('');
  const [careerId, setCareerId] = useState('');
  const [currentYear, setCurrentYear] = useState<number | ''>('');
  const [bio, setBio] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // The form resets every time it opens.
  useEffect(() => {
    if (!open) return;
    setError('');
    setName(initial?.name ?? '');
    setLevel(initial?.level ?? '');
    setCity(initial?.city ?? '');
    setUniversityId(initial?.universityId ?? '');
    setUnitId(initial?.academicUnitId ?? '');
    setCareerId(initial?.careerId ?? '');
    setCurrentYear(initial?.currentYear ?? '');
    setBio(initial?.bio ?? '');
  }, [open, initial]);

  useEffect(() => {
    if (!open) return;
    setUniversities([]);
    api
      .universities()
      .then(setUniversities)
      .catch(() => setError('No pudimos cargar las universidades.'));
  }, [open]);

  // Loaders depend on `open` as well as on the selected value: reopening the form with the
  // same university keeps `universityId` unchanged, and a loader keyed only on it would
  // never run again (that is why the faculty looked lost after saving).
  useEffect(() => {
    if (!open || !universityId) {
      setUnits([]);
      return;
    }
    let cancelled = false;
    api
      .academicUnits(universityId)
      .then((list) => {
        if (!cancelled) setUnits(list);
      })
      .catch(() => {
        if (!cancelled) setError('No pudimos cargar las facultades.');
      });
    return () => {
      cancelled = true;
    };
  }, [open, universityId]);

  useEffect(() => {
    if (!open || !unitId) {
      setCareers([]);
      return;
    }
    let cancelled = false;
    api
      .careers(unitId)
      .then((list) => {
        if (!cancelled) setCareers(list);
      })
      .catch(() => {
        if (!cancelled) setError('No pudimos cargar las carreras.');
      });
    return () => {
      cancelled = true;
    };
  }, [open, unitId]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError('Ingresá tu nombre y apellido.');
      return;
    }
    if (!level) {
      setError('Elegí tu nivel.');
      return;
    }

    const hasAcademicData = Boolean(
      universityId || unitId || careerId || currentYear !== '',
    );
    if (hasAcademicData && (!universityId || !careerId)) {
      setError('Para completar datos académicos, elegí universidad y carrera.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const input: StudentProfileInput = {
        name: name.trim(),
        level,
        city: city.trim() || undefined,
        bio: bio.trim() || undefined,
        ...(hasAcademicData
          ? {
              universityId,
              careerId,
              currentYear: currentYear === '' ? undefined : currentYear,
            }
          : {}),
      };
      const saved = initial ? await api.updateStudentProfile(input) : await api.createStudentProfile(input);
      onSaved(saved);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError('Revisá los datos del perfil.');
      } else if (e instanceof ApiError && e.status === 409) {
        setError('Ya tenés un perfil de estudiante.');
      } else if (e instanceof ApiError && e.status === 429) {
        setError('Demasiados intentos. Esperá unos minutos e intentá de nuevo.');
      } else {
        setError('No pudimos guardar tu perfil. Intentá de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  }

  const academicSummary =
    initial?.universityName && initial?.careerName
      ? `${initial.universityName} · ${initial.careerName}${
          initial.currentYear ? ` · Año ${initial.currentYear}` : ''
        }`
      : 'Sin datos académicos';

  return (
    <Modal open={open} onClose={onClose} title="Mi perfil de estudiante" size="sm">
      <form className="space-y-4" onSubmit={submit}>
        <p className="text-sm text-content-muted">
          Con tu nombre y nivel ya podés contactar profesores y guardar favoritos. Los datos
          académicos son opcionales.
        </p>

        {initial && (
          <p className="rounded-xl bg-surface-muted px-3 py-2 text-xs text-content-muted">
            Guardado: {initial.name || 'Sin nombre'} · {levelLabel(initial.level)} ·{' '}
            {initial.city || 'Sin ciudad'} · {academicSummary}
          </p>
        )}

        <Field label="Nombre y apellido">
          {(props) => (
            <Input
              {...props}
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="name"
              maxLength={255}
              required
            />
          )}
        </Field>

        <Field label="Nivel">
          {(props) => (
            <Select
              {...props}
              value={level}
              onChange={(e) => setLevel(e.target.value as StudentLevel)}
              required
            >
              <option value="">Elegí tu nivel</option>
              {LEVELS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Ciudad" hint="Opcional">
          {(props) => (
            <Input
              {...props}
              type="text"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              autoComplete="address-level2"
              maxLength={100}
            />
          )}
        </Field>

        <fieldset className="space-y-4 rounded-xl border border-border p-4">
          <legend className="px-1 text-sm font-semibold">Datos académicos (opcional)</legend>
          <p className="text-xs text-content-muted">
            Completalos solo si cursás una carrera. Si no aplica, dejalos vacíos.
          </p>

          <Field label="Universidad">
            {(props) => (
              <Select
                {...props}
                value={universityId}
                onChange={(e) => {
                  const value = e.target.value;
                  setUniversityId(value);
                  setUnitId('');
                  setCareerId('');
                  // The previous university's faculties no longer apply: showing them while the
                  // new list loads allowed saving an inconsistent university + career pair.
                  setUnits([]);
                  setCareers([]);
                  if (!value) setCurrentYear('');
                }}
              >
                <option value="">Sin datos académicos</option>
                {universities.map((university) => (
                  <option key={university.id} value={university.id}>
                    {university.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>

          <Field label="Facultad">
            {(props) => (
              <Select
                {...props}
                value={unitId}
                disabled={!universityId}
                onChange={(e) => {
                  const value = e.target.value;
                  setUnitId(value);
                  setCareerId('');
                  // Same reason as above: the previous faculty's careers cannot be offered.
                  setCareers([]);
                  if (!value) setCurrentYear('');
                }}
              >
                <option value="">Elegí tu facultad</option>
                {units.map((unit) => (
                  <option key={unit.id} value={unit.id}>
                    {unit.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>

          <Field label="Carrera">
            {(props) => (
              <Select
                {...props}
                value={careerId}
                disabled={!unitId}
                onChange={(e) => {
                  setCareerId(e.target.value);
                  if (!e.target.value) setCurrentYear('');
                }}
              >
                <option value="">Elegí tu carrera</option>
                {careers.map((career) => (
                  <option key={career.id} value={career.id}>
                    {career.name}
                  </option>
                ))}
              </Select>
            )}
          </Field>

          <Field label="Año que cursás">
            {(props) => (
              <Select
                {...props}
                value={currentYear === '' ? '' : String(currentYear)}
                disabled={!careerId}
                onChange={(e) => setCurrentYear(e.target.value === '' ? '' : Number(e.target.value))}
              >
                <option value="">Sin especificar</option>
                {Array.from({ length: 12 }).map((_, index) => (
                  <option key={index + 1} value={index + 1}>
                    {index + 1}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        </fieldset>

        <Field label="Sobre vos" hint="Opcional">
          {(props) => (
            <Textarea
              {...props}
              rows={2}
              maxLength={MAX_STUDENT_BIO_LENGTH}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
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
            Guardar perfil
          </Button>
          <Button type="button" variant="outline" block onClick={onClose}>
            Cancelar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
