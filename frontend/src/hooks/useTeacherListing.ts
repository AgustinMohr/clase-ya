import { useEffect, useState } from 'react';
import {
  api,
  ApiError,
  type AnnouncementStatus,
  type AvailabilityWindow,
  type OwnEducation,
  type OwnSubject,
  type Subject,
  type TeacherProfile,
  type TeacherProfileInput,
  type TeacherVerification,
} from '../api';
import { useToast } from '../components/ui/Toast';
import { toProfileInput } from '../lib/teacherProfile';

export interface EducationForm {
  institution: string;
  degree: string;
  description: string;
  startYear: string;
  endYear: string;
}

export interface AvailabilityForm {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  mode: string;
}

const EMPTY_EDUCATION: EducationForm = {
  institution: '',
  degree: '',
  description: '',
  startYear: '',
  endYear: '',
};

const EMPTY_AVAILABILITY: AvailabilityForm = { dayOfWeek: 1, startTime: '', endTime: '', mode: '' };

/**
 * State and actions for "Mi anuncio": loads the profile and the announcement pieces, and owns the
 * mutations (profile, subjects, modalities, education, availability). The page only orchestrates.
 */
export function useTeacherListing() {
  const { notify } = useToast();

  const [profile, setProfile] = useState<TeacherProfile | null>(null);
  const [announcement, setAnnouncement] = useState<AnnouncementStatus | null>(null);
  const [verification, setVerification] = useState<TeacherVerification | null>(null);
  const [subjects, setSubjects] = useState<OwnSubject[]>([]);
  const [modalities, setModalities] = useState<string[]>([]);
  const [pendingModalities, setPendingModalities] = useState<Set<string>>(new Set());
  const [education, setEducation] = useState<OwnEducation[]>([]);
  const [availability, setAvailability] = useState<AvailabilityWindow[]>([]);
  const [availabilityTotal, setAvailabilityTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const [form, setForm] = useState<TeacherProfileInput>(toProfileInput(null));
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState('');

  const [addingSubject, setAddingSubject] = useState(false);
  const [eduForm, setEduForm] = useState<EducationForm>(EMPTY_EDUCATION);
  const [addingEducation, setAddingEducation] = useState(false);
  const [availForm, setAvailForm] = useState<AvailabilityForm>(EMPTY_AVAILABILITY);
  const [addingAvailability, setAddingAvailability] = useState(false);

  useEffect(() => {
    let active = true;
    api
      .myProfile()
      .then((saved) => {
        if (!active) return;
        setProfile(saved);
        setForm(toProfileInput(saved));
      })
      .catch((error) => {
        // A teacher without a profile gets 404; the page then offers the create form.
        if (active && !(error instanceof ApiError && error.status === 404)) {
          notify('No pudimos cargar tu anuncio.', 'error');
        }
      });

    Promise.all([
      api.announcement().catch(() => null),
      api.myVerification().catch(() => null),
      api.mySubjects().catch(() => []),
      api.myModalities().then((list) => list.map((m) => m.modality)).catch(() => []),
      api.myEducation().catch(() => []),
      api.myAvailability().catch(() => null),
    ]).then(([ann, ver, subs, mods, edu, avail]) => {
      if (!active) return;
      setAnnouncement(ann);
      setVerification(ver);
      setSubjects(subs);
      setModalities(mods);
      setEducation(edu);
      setAvailability(avail?.content ?? []);
      setAvailabilityTotal(avail?.totalElements ?? 0);
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
      setForm(toProfileInput(saved));
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

  const searchSubjects = async (term: string): Promise<Subject[]> => {
    const page = await api.subjectSearch(term, 10);
    return page.content;
  };

  async function addSubjectBySubject(subjectId: string): Promise<boolean> {
    if (!subjectId) return false;
    setAddingSubject(true);
    try {
      await api.addSubjectBySubject(subjectId);
      setSubjects(await api.mySubjects());
      reloadAnnouncement();
      notify('Materia agregada');
      return true;
    } catch {
      notify('No pudimos agregar la materia.', 'error');
      return false;
    } finally {
      setAddingSubject(false);
    }
  }

  /** Removing a subject is fully reversible: the undo re-adds the same target. */
  async function removeSubject(subject: OwnSubject) {
    const restore = subject.subjectId
      ? () => api.addSubjectBySubject(subject.subjectId!)
      : () => api.addSubject(subject.careerSubjectId!);
    try {
      await api.removeSubject(subject.id);
      setSubjects((prev) => prev.filter((s) => s.id !== subject.id));
      reloadAnnouncement();
      notify(`${subject.subjectName ?? 'Materia'} quitada`, {
        action: {
          label: 'Deshacer',
          onClick: async () => {
            try {
              await restore();
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
    if (pendingModalities.has(modality)) return;
    const present = modalities.includes(modality);
    setPendingModalities((prev) => new Set(prev).add(modality));
    setModalities((prev) =>
      present ? prev.filter((m) => m !== modality) : [...prev, modality],
    );
    try {
      if (present) {
        await api.removeModality(modality);
      } else {
        await api.addModality(modality);
      }
      reloadAnnouncement();
    } catch {
      setModalities((prev) =>
        present
          ? prev.includes(modality) ? prev : [...prev, modality]
          : prev.filter((m) => m !== modality),
      );
      notify('No pudimos actualizar las modalidades.', 'error');
    } finally {
      setPendingModalities((prev) => {
        const next = new Set(prev);
        next.delete(modality);
        return next;
      });
    }
  }

  async function addEducation() {
    if (!eduForm.institution || !eduForm.degree) return;
    setAddingEducation(true);
    try {
      await api.addEducation({
        institution: eduForm.institution,
        degree: eduForm.degree,
        description: eduForm.description || undefined,
        startYear: eduForm.startYear ? Number(eduForm.startYear) : undefined,
        endYear: eduForm.endYear ? Number(eduForm.endYear) : undefined,
      });
      setEducation(await api.myEducation());
      setEduForm(EMPTY_EDUCATION);
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
   * Removing education may drop its documents and verification state, which an undo could not
   * restore → confirmation, not undo (DESIGN.md §12). The confirm dialog lives in the section.
   */
  async function removeEducation(item: OwnEducation) {
    try {
      await api.removeEducation(item.id);
      setEducation((prev) => prev.filter((e) => e.id !== item.id));
      reloadAnnouncement();
      reloadVerification();
      notify('Formación quitada');
    } catch {
      notify('No pudimos quitar la formación.', 'error');
    }
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
      setAvailabilityTotal(page.totalElements);
      setAvailForm(EMPTY_AVAILABILITY);
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

  return {
    loading,
    profile,
    announcement,
    verification,
    subjects,
    modalities,
    pendingModalities,
    education,
    availability,
    availabilityTotal,
    form,
    setForm,
    savingProfile,
    profileError,
    saveProfile,
    reloadVerification,
    addingSubject,
    searchSubjects,
    addSubjectBySubject,
    removeSubject,
    toggleModality,
    eduForm,
    setEduForm,
    addingEducation,
    addEducation,
    removeEducation,
    availForm,
    setAvailForm,
    addingAvailability,
    addAvailability,
    disableAvailability,
  };
}
