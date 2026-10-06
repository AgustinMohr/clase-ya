import { useEffect, useState } from 'react';
import { ArrowLeft, BadgeCheck, Check, Circle, Eye, Megaphone, Plus, Trash2 } from 'lucide-react';
import {
  api,
  ApiError,
  MAX_TEACHER_BIO_LENGTH,
  type AcademicUnit,
  type AnnouncementStatus,
  type AvailabilityWindow,
  type Career,
  type CareerSubject,
  type OwnEducation,
  type OwnSubject,
  type TeacherProfile,
  type TeacherProfileInput,
  type TeacherVerification,
  type University,
} from '../api';
import { useToast } from '../components/ui/Toast';
import { Badge, Card, ConfirmDialog, EmptyState, Skeleton, SubCard, Tag } from '../components/ui/primitives';
import { Button } from '../components/ui/Button';
import { Field, Input, Select, Textarea } from '../components/ui/Field';
import VerificationPanel from '../components/teacher/VerificationPanel';
import { dayName, modalityLabel } from '../lib/format';

const DAYS = [1, 2, 3, 4, 5, 6, 7];

const EMPTY_PROFILE: TeacherProfileInput = {
  name: '',
  bio: '',
  address: '',
  availabilityNote: '',
  pricePerHour: undefined,
  city: '',
  photoUrl: '',
  latitude: undefined,
  longitude: undefined,
};

type ConfirmState = null | {
  title: string;
  description?: string;
  confirmLabel?: string;
  onConfirm: () => void | Promise<void>;
};

export default function MyListingPage({
  onBack,
  onPreview,
}: {
  onBack: () => void;
  onPreview: (id: string) => void;
}) {
  const { notify } = useToast();

  const [profile, setProfile] = useState<TeacherProfile | null>(null);
  const [announcement, setAnnouncement] = useState<AnnouncementStatus | null>(null);
  const [verification, setVerification] = useState<TeacherVerification | null>(null);
  const [subjects, setSubjects] = useState<OwnSubject[]>([]);
  const [modalities, setModalities] = useState<string[]>([]);
  const [education, setEducation] = useState<OwnEducation[]>([]);
  const [availability, setAvailability] = useState<AvailabilityWindow[]>([]);
  const [loading, setLoading] = useState(true);

  // Profile form (pre-filled; lat/long preserved but not edited here).
  const [form, setForm] = useState<TeacherProfileInput>(EMPTY_PROFILE);
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [confirm, setConfirm] = useState<ConfirmState>(null);

  // Subject picker cascade.
  const [universities, setUniversities] = useState<University[]>([]);
  const [units, setUnits] = useState<AcademicUnit[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [careerSubjects, setCareerSubjects] = useState<CareerSubject[]>([]);
  const [selUniversity, setSelUniversity] = useState('');
  const [selUnit, setSelUnit] = useState('');
  const [selCareer, setSelCareer] = useState('');
  const [selCareerSubject, setSelCareerSubject] = useState('');
  const [addingSubject, setAddingSubject] = useState(false);

  // Education form.
  const [eduForm, setEduForm] = useState<{ institution: string; degree: string; description: string; startYear: string; endYear: string }>({
    institution: '',
    degree: '',
    description: '',
    startYear: '',
    endYear: '',
  });
  const [addingEducation, setAddingEducation] = useState(false);

  // Availability form.
  const [availForm, setAvailForm] = useState<{ dayOfWeek: number; startTime: string; endTime: string; mode: string }>({
    dayOfWeek: 1,
    startTime: '',
    endTime: '',
    mode: '',
  });
  const [addingAvailability, setAddingAvailability] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .myProfile()
      .then((p) => {
        if (!active) return;
        setProfile(p);
        setForm({
          name: p.name ?? '',
          bio: p.bio ?? '',
          address: p.address ?? '',
          availabilityNote: p.availabilityNote ?? '',
          pricePerHour: p.pricePerHour,
          city: p.city ?? '',
          photoUrl: p.photoUrl ?? '',
          latitude: p.latitude,
          longitude: p.longitude,
        });
      })
      .catch((e) => {
        // A teacher without a profile gets 404; the page then offers the create form.
        if (active && !(e instanceof ApiError && e.status === 404)) {
          notify('No pudimos cargar tu anuncio.', 'error');
        }
      });
    Promise.all([
      api.announcement().catch(() => null),
      api.myVerification().catch(() => null),
      api.mySubjects().catch(() => []),
      api.myModalities().then((list) => list.map((m) => m.modality)).catch(() => []),
      api.myEducation().catch(() => []),
      api.myAvailability().then((p) => p.content).catch(() => []),
      api.universities().catch(() => []),
    ]).then(([ann, ver, subs, mods, edu, avail, unis]) => {
      if (!active) return;
      setAnnouncement(ann);
      setVerification(ver);
      setSubjects(subs);
      setModalities(mods);
      setEducation(edu);
      setAvailability(avail);
      setUniversities(unis);
      setLoading(false);
    });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function reloadAnnouncement() {
    api.announcement().then(setAnnouncement).catch(() => undefined);
  }

  function reloadVerification() {
    api.myVerification().then(setVerification).catch(() => undefined);
  }

  async function saveProfile() {
    setSavingProfile(true);
    try {
      const saved = profile ? await api.updateTeacherProfile(form) : await api.createTeacherProfile(form);
      setProfile(saved);
      setForm({
        name: saved.name ?? '',
        bio: saved.bio ?? '',
        address: saved.address ?? '',
        availabilityNote: saved.availabilityNote ?? '',
        pricePerHour: saved.pricePerHour,
        city: saved.city ?? '',
        photoUrl: saved.photoUrl ?? '',
        latitude: saved.latitude,
        longitude: saved.longitude,
      });
      reloadAnnouncement();
      setProfileError('');
      notify('Perfil guardado');
    } catch {
      // Server-side error: shown inline (it persists) instead of only in a toast.
      setProfileError('No pudimos guardar el perfil. Revisá tu conexión e intentá de nuevo.');
    } finally {
      setSavingProfile(false);
    }
  }

  async function addSubject() {
    if (!selCareerSubject) return;
    setAddingSubject(true);
    try {
      await api.addSubject(selCareerSubject);
      setSubjects(await api.mySubjects());
      setSelCareerSubject('');
      reloadAnnouncement();
      notify('Materia agregada');
    } catch {
      notify('No pudimos agregar la materia.', 'error');
    } finally {
      setAddingSubject(false);
    }
  }

  /** Removing a subject is fully reversible: the undo re-adds the same careerSubjectId. */
  async function removeSubject(subject: OwnSubject) {
    try {
      await api.removeSubject(subject.careerSubjectId);
      setSubjects((prev) => prev.filter((s) => s.careerSubjectId !== subject.careerSubjectId));
      reloadAnnouncement();
      notify(`${subject.subjectName ?? 'Materia'} quitada`, {
        action: {
          label: 'Deshacer',
          onClick: async () => {
            try {
              await api.addSubject(subject.careerSubjectId);
              setSubjects(await api.mySubjects());
              reloadAnnouncement();
              notify('Materia restaurada');
            } catch {
              notify('No pudimos restaurar la materia.', 'error');
            }
          },
        },
      });
    } catch {
      notify('No pudimos quitar la materia.', 'error');
    }
  }

  async function toggleModality(modality: string) {
    const present = modalities.includes(modality);
    try {
      if (present) {
        await api.removeModality(modality);
        setModalities((prev) => prev.filter((m) => m !== modality));
      } else {
        await api.addModality(modality);
        setModalities((prev) => [...prev, modality]);
      }
      reloadAnnouncement();
    } catch {
      notify('No pudimos actualizar las modalidades.', 'error');
    }
  }

  async function addEducation() {
    if (!eduForm.institution || !eduForm.degree) return;
    setAddingEducation(true);
    try {
      const body = {
        institution: eduForm.institution,
        degree: eduForm.degree,
        description: eduForm.description || undefined,
        startYear: eduForm.startYear ? Number(eduForm.startYear) : undefined,
        endYear: eduForm.endYear ? Number(eduForm.endYear) : undefined,
      };
      await api.addEducation(body);
      setEducation(await api.myEducation());
      setEduForm({ institution: '', degree: '', description: '', startYear: '', endYear: '' });
      reloadAnnouncement();
      reloadVerification();
      notify('Formación agregada');
    } catch {
      notify('No pudimos agregar la formación.', 'error');
    } finally {
      setAddingEducation(false);
    }
  }

  /**
   * Removing education may drop its documents and verification state, which an
   * undo could not restore → confirmation, not undo (DESIGN.md §12).
   */
  function requestRemoveEducation(educationItem: OwnEducation) {
    setConfirm({
      title: 'Quitar formación',
      description: `Se quitará "${educationItem.degree}" y sus documentos asociados. Esta acción no se puede deshacer.`,
      confirmLabel: 'Quitar formación',
      onConfirm: async () => {
        try {
          await api.removeEducation(educationItem.id);
          setEducation((prev) => prev.filter((e) => e.id !== educationItem.id));
          reloadAnnouncement();
          reloadVerification();
          notify('Formación quitada');
        } catch {
          notify('No pudimos quitar la formación.', 'error');
        }
      },
    });
  }

  async function addAvailability() {
    if (!availForm.startTime || !availForm.endTime) return;
    setAddingAvailability(true);
    try {
      await api.addAvailability({
        dayOfWeek: availForm.dayOfWeek,
        startTime: availForm.startTime,
        endTime: availForm.endTime,
        mode: availForm.mode || undefined,
      });
      const page = await api.myAvailability();
      setAvailability(page.content);
      setAvailForm({ dayOfWeek: 1, startTime: '', endTime: '', mode: '' });
      reloadAnnouncement();
      notify('Horario agregado');
    } catch {
      notify('No pudimos agregar el horario.', 'error');
    } finally {
      setAddingAvailability(false);
    }
  }

  /** Disabling a window is reversible: the undo calls enableAvailability. */
  async function disableAvailability(window: AvailabilityWindow) {
    try {
      await api.disableAvailability(window.id);
      setAvailability((prev) => prev.map((w) => (w.id === window.id ? { ...w, status: 'DISABLED' } : w)));
      reloadAnnouncement();
      notify('Horario desactivado', {
        action: {
          label: 'Deshacer',
          onClick: async () => {
            try {
              await api.enableAvailability(window.id);
              setAvailability((prev) => prev.map((w) => (w.id === window.id ? { ...w, status: 'AVAILABLE' } : w)));
              reloadAnnouncement();
              notify('Horario reactivado');
            } catch {
              notify('No pudimos reactivar el horario.', 'error');
            }
          },
        },
      });
    } catch {
      notify('No pudimos desactivar el horario.', 'error');
    }
  }

  function pickUnit(universityId: string) {
    setSelUniversity(universityId);
    setSelUnit('');
    setSelCareer('');
    setSelCareerSubject('');
    setCareers([]);
    setCareerSubjects([]);
    api.academicUnits(universityId).then(setUnits).catch(() => setUnits([]));
  }

  function pickCareer(unitId: string) {
    setSelUnit(unitId);
    setSelCareer('');
    setSelCareerSubject('');
    setCareerSubjects([]);
    api.careers(unitId).then(setCareers).catch(() => setCareers([]));
  }

  function pickCareerSubject(careerId: string) {
    setSelCareer(careerId);
    setSelCareerSubject('');
    api.careerSubjects(careerId).then(setCareerSubjects).catch(() => setCareerSubjects([]));
  }

  if (loading) {
    return (
      <div className="container-page space-y-5 py-8">
        <Skeleton className="h-24" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  const checklist = [
    { done: announcement?.hasName, label: 'Nombre' },
    { done: announcement?.hasBio, label: 'Bio' },
    { done: announcement?.hasSubject, label: 'Al menos una materia' },
    { done: announcement?.hasModality, label: 'Al menos una modalidad' },
    { done: announcement?.hasAvailability, label: 'Al menos un horario' },
  ];

  return (
    <div className="container-page py-8">
      <button
        type="button"
        onClick={onBack}
        className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-content-muted transition-colors hover:text-primary-600 dark:hover:text-primary-200"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver
      </button>

      <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 font-display text-3xl">
            <Megaphone className="h-7 w-7 text-primary-600 dark:text-primary-200" aria-hidden="true" /> Mi anuncio
          </h1>
          <p className="mt-1 text-content-muted">
            Mantené tu perfil, materias, modalidades, formación y disponibilidad. La verificación no
            bloquea tu anuncio: lo que falta es solo informativo.
          </p>
        </div>
        {profile && (
          <Button variant="outline" onClick={() => onPreview(profile.id)}>
            <Eye className="h-4 w-4" aria-hidden="true" /> Previsualizar
          </Button>
        )}
      </header>

      <div className="space-y-8">
        {/* COMPLETITUD */}
        <Card className="border-primary-200 p-6 ring-1 ring-primary-100 dark:ring-primary-900/40">
          <h2 className="mb-4 font-display text-xl">Estado del anuncio</h2>
          <div className="mb-3">
            {announcement?.completeness === 'PUBLISHED' ? (
              <Badge tone="success" icon={<BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />}>Publicado</Badge>
            ) : (
              <Badge tone="neutral">Incompleto</Badge>
            )}
          </div>
          <ul className="space-y-1.5 text-sm">
            {checklist.map((item) => (
              <li key={item.label} className="flex items-center gap-2">
                {item.done ? (
                  <Check className="h-4 w-4 shrink-0 text-success-600 dark:text-success-500" aria-hidden="true" />
                ) : (
                  <Circle className="h-4 w-4 shrink-0 text-content-muted" aria-hidden="true" />
                )}
                <span className={item.done ? 'text-content' : 'text-content-muted'}>
                  {item.label}
                  <span className="sr-only">{item.done ? ' — completado' : ' — pendiente'}</span>
                </span>
              </li>
            ))}
          </ul>
        </Card>

        {/* VERIFICACIÓN */}
        <div>
          <VerificationPanel verification={verification} onChanged={reloadVerification} />
        </div>

        {/* PERFIL */}
        <Card className="p-6">
          <h2 className="mb-4 font-display text-xl">{profile ? 'Perfil' : 'Creá tu perfil de profesor'}</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Nombre">
              {({ id }) => <Input id={id} value={form.name ?? ''} onChange={(e) => setForm({ ...form, name: e.target.value })} />}
            </Field>
            <Field label="Ciudad">
              {({ id }) => <Input id={id} value={form.city ?? ''} onChange={(e) => setForm({ ...form, city: e.target.value })} />}
            </Field>
            <Field label="Precio por hora (ARS)" hint="Dejalo vacío para 'a convenir'.">
              {({ id }) => (
                <Input
                  id={id}
                  type="number"
                  min="0"
                  value={form.pricePerHour ?? ''}
                  onChange={(e) => setForm({ ...form, pricePerHour: e.target.value === '' ? undefined : Number(e.target.value) })}
                />
              )}
            </Field>
            <Field label="Foto (URL)">
              {({ id }) => <Input id={id} value={form.photoUrl ?? ''} onChange={(e) => setForm({ ...form, photoUrl: e.target.value })} />}
            </Field>
            <Field label="Dirección (zona)">
              {({ id }) => <Input id={id} value={form.address ?? ''} onChange={(e) => setForm({ ...form, address: e.target.value })} />}
            </Field>
            <Field label="Nota de disponibilidad">
              {({ id }) => <Input id={id} value={form.availabilityNote ?? ''} onChange={(e) => setForm({ ...form, availabilityNote: e.target.value })} />}
            </Field>
          </div>
          <div className="mt-4">
            <Field label="Bio">
              {({ id }) => (
                <Textarea id={id} rows={3} maxLength={MAX_TEACHER_BIO_LENGTH} value={form.bio ?? ''} onChange={(e) => setForm({ ...form, bio: e.target.value })} />
              )}
            </Field>
          </div>
          {profileError && (
            <p
              role="alert"
              className="mt-4 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500"
            >
              {profileError}
            </p>
          )}
          <div className="mt-4">
            <Button onClick={saveProfile} loading={savingProfile}>
              {profile ? 'Guardar cambios' : 'Crear perfil'}
            </Button>
          </div>
        </Card>

        {/* MATERIAS */}
        <Card className="p-6">
          <h2 className="mb-4 font-display text-xl">Materias</h2>
          {subjects.length > 0 ? (
            <ul className="mb-4 flex flex-wrap gap-2">
              {subjects.map((s) => (
                <li key={s.careerSubjectId}>
                  <Tag tone="primary" data-testid="subject-tag" onRemove={() => removeSubject(s)} removeLabel={`Quitar ${s.subjectName ?? 'materia'}`}>
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
                <Select id={id} value={selUniversity} onChange={(e) => pickUnit(e.target.value)}>
                  <option value="">Elegir…</option>
                  {universities.map((u) => (
                    <option key={u.id} value={u.id}>{u.name}</option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Unidad académica">
              {({ id }) => (
                <Select id={id} value={selUnit} disabled={!selUniversity} onChange={(e) => pickCareer(e.target.value)}>
                  <option value="">Elegir…</option>
                  {units.map((u) => (
                    <option key={u.id} value={u.id}>{u.name}</option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Carrera">
              {({ id }) => (
                <Select id={id} value={selCareer} disabled={!selUnit} onChange={(e) => pickCareerSubject(e.target.value)}>
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
            <Button variant="outline" onClick={addSubject} loading={addingSubject} disabled={!selCareerSubject}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Agregar materia
            </Button>
          </div>
        </Card>

        {/* MODALIDADES */}
        <Card className="p-6">
          <h2 className="mb-4 font-display text-xl">Modalidades</h2>
          <div className="flex flex-wrap gap-2">
            {(['ONLINE', 'IN_PERSON'] as const).map((m) => {
              const active = modalities.includes(m);
              return (
                <Button key={m} variant={active ? 'primary' : 'outline'} size="sm" onClick={() => toggleModality(m)} aria-pressed={active}>
                  {active && <Check className="h-4 w-4" aria-hidden="true" />}
                  {modalityLabel(m)}
                </Button>
              );
            })}
          </div>
        </Card>

        {/* FORMACIÓN */}
        <Card className="p-6">
          <h2 className="mb-4 font-display text-xl">Formación</h2>
          {education.length > 0 ? (
            <ul className="mb-4 space-y-2 text-sm">
              {education.map((e) => (
                <li key={e.id}>
                  <SubCard dense className="flex items-start justify-between gap-3">
                    <div>
                      <span className="font-semibold">{e.degree}</span> — {e.institution}
                      {e.startYear ? ` (${e.startYear}${e.endYear ? `–${e.endYear}` : ''})` : ''}
                      {e.isVerified && <Badge tone="success" className="ml-2">Verificada</Badge>}
                    </div>
                    <button type="button" onClick={() => requestRemoveEducation(e)} aria-label={`Quitar ${e.degree}`} className="text-content-muted hover:text-error-600">
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
              {({ id }) => <Input id={id} value={eduForm.institution} onChange={(e) => setEduForm({ ...eduForm, institution: e.target.value })} />}
            </Field>
            <Field label="Título">
              {({ id }) => <Input id={id} value={eduForm.degree} onChange={(e) => setEduForm({ ...eduForm, degree: e.target.value })} />}
            </Field>
            <Field label="Año de inicio">
              {({ id }) => <Input id={id} type="number" value={eduForm.startYear} onChange={(e) => setEduForm({ ...eduForm, startYear: e.target.value })} />}
            </Field>
            <Field label="Año de fin">
              {({ id }) => <Input id={id} type="number" value={eduForm.endYear} onChange={(e) => setEduForm({ ...eduForm, endYear: e.target.value })} />}
            </Field>
          </div>
          <div className="mt-4">
            <Field label="Descripción">
              {({ id }) => <Textarea id={id} rows={2} value={eduForm.description} onChange={(e) => setEduForm({ ...eduForm, description: e.target.value })} />}
            </Field>
          </div>
          <div className="mt-4">
            <Button variant="outline" onClick={addEducation} loading={addingEducation} disabled={!eduForm.institution || !eduForm.degree}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Agregar formación
            </Button>
          </div>
        </Card>

        {/* DISPONIBILIDAD */}
        <Card className="p-6">
          <h2 className="mb-4 font-display text-xl">Disponibilidad semanal</h2>
          {availability.length > 0 ? (
            <ul className="mb-4 space-y-2 text-sm">
              {availability.map((w) => (
                <li key={w.id}>
                  <SubCard dense className="flex flex-wrap items-center gap-2">
                    <span className="w-24 font-semibold">{dayName(w.dayOfWeek)}</span>
                    <Badge tone={w.status === 'DISABLED' ? 'neutral' : 'accent'}>
                      {w.startTime}–{w.endTime}
                      {w.mode ? ` · ${modalityLabel(w.mode)}` : ''}
                    </Badge>
                    {w.status !== 'DISABLED' && (
                      <button type="button" onClick={() => disableAvailability(w)} className="text-xs font-semibold text-content-muted hover:text-error-600">
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
                <Select id={id} value={availForm.dayOfWeek} onChange={(e) => setAvailForm({ ...availForm, dayOfWeek: Number(e.target.value) })}>
                  {DAYS.map((d) => (
                    <option key={d} value={d}>{dayName(d)}</option>
                  ))}
                </Select>
              )}
            </Field>
            <Field label="Desde">
              {({ id }) => <Input id={id} type="time" value={availForm.startTime} onChange={(e) => setAvailForm({ ...availForm, startTime: e.target.value })} />}
            </Field>
            <Field label="Hasta">
              {({ id }) => <Input id={id} type="time" value={availForm.endTime} onChange={(e) => setAvailForm({ ...availForm, endTime: e.target.value })} />}
            </Field>
            <Field label="Modalidad">
              {({ id }) => (
                <Select id={id} value={availForm.mode} onChange={(e) => setAvailForm({ ...availForm, mode: e.target.value })}>
                  <option value="">Ambas</option>
                  <option value="ONLINE">Online</option>
                  <option value="IN_PERSON">Presencial</option>
                </Select>
              )}
            </Field>
          </div>
          <div className="mt-4">
            <Button variant="outline" onClick={addAvailability} loading={addingAvailability} disabled={!availForm.startTime || !availForm.endTime}>
              <Plus className="h-4 w-4" aria-hidden="true" /> Agregar horario
            </Button>
          </div>
        </Card>
      </div>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm?.title ?? ''}
        description={confirm?.description}
        confirmLabel={confirm?.confirmLabel}
        onConfirm={() => confirm?.onConfirm()}
        onClose={() => setConfirm(null)}
      />
    </div>
  );
}
