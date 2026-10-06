import { useEffect, useState } from 'react';
import { api, ApiError, MAX_STUDENT_BIO_LENGTH, type AcademicUnit, type Career, type StudentProfile, type University } from '../api';
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

/**
 * Student profile form (CONTACT-001). It is a prerequisite of the product's core
 * flow: without a profile the API answers 409 both for contacting a teacher and
 * for saving favorites.
 */
export default function StudentProfileModal({ open, onClose, onSaved, initial }: Props) {
  const [universities, setUniversities] = useState<University[]>([]);
  const [units, setUnits] = useState<AcademicUnit[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [universityId, setUniversityId] = useState('');
  const [unitId, setUnitId] = useState('');
  const [careerId, setCareerId] = useState('');
  const [currentYear, setCurrentYear] = useState(2);
  const [bio, setBio] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // Prefilling the unit of an existing profile needs a scan over its units.
  const [prefillDone, setPrefillDone] = useState(false);

  // The form resets every time it opens.
  useEffect(() => {
    if (!open) return;
    setPrefillDone(false);
    setError('');
    setUniversityId(initial?.universityId ?? '');
    setUnitId('');
    setCareerId(initial?.careerId ?? '');
    setCurrentYear(initial?.currentYear ?? 2);
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

  // Editing: resolve which unit owns the profile's career so the cascade is preselected.
  // Careers are fetched for every unit at once (there are only a few) and a failing unit
  // never aborts the scan; the loader above then fills the list for the resolved unit.
  useEffect(() => {
    if (!open || prefillDone) return;
    if (!initial) {
      setPrefillDone(true);
      return;
    }
    if (universityId !== initial.universityId || units.length === 0) return;
    let cancelled = false;
    void (async () => {
      const resolved = await Promise.all(
        units.map(async (unit) => ({ unit, careers: await api.careers(unit.id).catch(() => []) })),
      );
      if (cancelled) return;
      const match = resolved.find((entry) => entry.careers.some((career) => career.id === initial.careerId));
      if (match) {
        setUnitId(match.unit.id);
        setCareerId(initial.careerId);
      }
      setPrefillDone(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [open, prefillDone, initial, units, universityId]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!universityId || !careerId) {
      setError('Elegí universidad, facultad y carrera.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const input = { universityId, careerId, currentYear, bio: bio.trim() || undefined };
      const saved = initial ? await api.updateStudentProfile(input) : await api.createStudentProfile(input);
      onSaved(saved);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No pudimos guardar tu perfil.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Mi perfil de estudiante" size="sm">
      <form className="space-y-4" onSubmit={submit}>
        <p className="text-sm text-content-muted">
          Con tu perfil completo podés contactar profesores y guardar favoritos.
        </p>

        {initial && (
          <p className="rounded-xl bg-surface-muted px-3 py-2 text-xs text-content-muted">
            Guardado: {initial.universityName} · {initial.careerName} · Año {initial.currentYear}
          </p>
        )}

        <Field label="Universidad">
          {(props) => (
            <Select
              {...props}
              value={universityId}
              onChange={(e) => {
                setUniversityId(e.target.value);
                setUnitId('');
                setCareerId('');
                // The previous university's faculties no longer apply: showing them while the
                // new list loads allowed saving an inconsistent university + career pair.
                setUnits([]);
                setCareers([]);
              }}
              required
            >
              <option value="">Elegí tu universidad</option>
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
                setUnitId(e.target.value);
                setCareerId('');
                // Same reason as above: the previous faculty's careers cannot be offered.
                setCareers([]);
              }}
              required
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
              onChange={(e) => setCareerId(e.target.value)}
              required
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
            <Select {...props} value={String(currentYear)} onChange={(e) => setCurrentYear(Number(e.target.value))}>
              {Array.from({ length: 12 }).map((_, index) => (
                <option key={index + 1} value={index + 1}>
                  {index + 1}
                </option>
              ))}
            </Select>
          )}
        </Field>

        <Field label="Sobre vos" hint="Opcional">
          {(props) => <Textarea {...props} rows={2} maxLength={MAX_STUDENT_BIO_LENGTH} value={bio} onChange={(e) => setBio(e.target.value)} />}
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
