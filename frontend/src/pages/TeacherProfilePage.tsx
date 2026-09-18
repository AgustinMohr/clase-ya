import { useEffect, useMemo, useState } from 'react';
import { ArrowLeft, BadgeCheck, CalendarDays, GraduationCap, Heart, MapPin, MessageCircle, Star } from 'lucide-react';
import { api, type AvailabilityWindow, type TeacherDetail, type TeacherSummary } from '../api';
import { Avatar, Badge, Card, EmptyState, Skeleton } from '../components/ui/primitives';
import { Button } from '../components/ui/Button';
import { cn } from '../lib/cn';
import { dayName, dayPartLabel, formatPriceHour, modalityLabel } from '../lib/format';

interface Props {
  teacherId: string;
  onBack: () => void;
  onContact: (teacher: TeacherSummary) => void;
  onToggleFavorite?: (teacher: TeacherSummary) => void;
  favorite: boolean;
}

export default function TeacherProfilePage({ teacherId, onBack, onContact, onToggleFavorite, favorite }: Props) {
  const [teacher, setTeacher] = useState<TeacherDetail | null>(null);
  const [windows, setWindows] = useState<AvailabilityWindow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all([api.teacher(teacherId), api.availabilityByTeacher(teacherId).catch(() => ({ content: [] }))])
      .then(([detail, availability]) => {
        if (!active) return;
        setTeacher(detail);
        setWindows(availability.content ?? []);
      })
      .catch(() => active && setError('No pudimos cargar el perfil.'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [teacherId]);

  const availabilityByDay = useMemo(() => {
    const grouped = new Map<number, AvailabilityWindow[]>();
    windows.forEach((w) => {
      if (!w.dayOfWeek) return;
      grouped.set(w.dayOfWeek, [...(grouped.get(w.dayOfWeek) ?? []), w]);
    });
    return [...grouped.entries()].sort((a, b) => a[0] - b[0]);
  }, [windows]);

  if (loading) {
    return (
      <div className="container-page space-y-5 py-8">
        <Skeleton className="h-40" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  if (error || !teacher) {
    return (
      <div className="container-page py-10">
        <EmptyState title="Perfil no disponible" description={error || 'No encontramos este profesor.'} action={<Button onClick={onBack}>Volver</Button>} />
      </div>
    );
  }

  const subjects = (teacher.subjects ?? []).filter((s) => s.subjectName);

  return (
    <div className="container-page py-8">
      <button
        type="button"
        onClick={onBack}
        className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-content-muted transition-colors hover:text-primary-600"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver
      </button>

      <div className="grid gap-8 lg:grid-cols-[1fr_320px]">
        <div className="space-y-6">
          {/* HERO */}
          <Card className="p-6">
            <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
              <Avatar src={teacher.photoUrl} name={teacher.displayName} size="xl" />
              <div className="flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="font-display text-3xl leading-tight">{teacher.displayName || 'Profesor'}</h1>
                  {teacher.verificationStatus === 'VERIFIED' && (
                    <Badge tone="success" icon={<BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />}>
                      Verificado
                    </Badge>
                  )}
                </div>
                <p className="mt-2 flex flex-wrap items-center gap-3 text-sm text-content-muted">
                  <span className="inline-flex items-center gap-1">
                    <Star className={cn('h-4 w-4', teacher.ratingCount ? 'fill-accent-400 text-accent-400' : 'text-content-muted/50')} aria-hidden="true" />
                    {teacher.ratingCount ? `${Number(teacher.ratingAverage).toFixed(1)} (${teacher.ratingCount} opiniones)` : 'Sin opiniones todavía'}
                  </span>
                  {teacher.city && (
                    <span className="inline-flex items-center gap-1">
                      <MapPin className="h-4 w-4" aria-hidden="true" /> {teacher.city}
                    </span>
                  )}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  {(teacher.modalities ?? []).map((m) => (
                    <Badge key={m} tone="primary">
                      {modalityLabel(m)}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
            {teacher.bio && <p className="mt-5 text-[15px] leading-relaxed text-content-muted">{teacher.bio}</p>}
          </Card>

          {/* MATERIAS */}
          {subjects.length > 0 && (
            <Card className="p-6">
              <h2 className="mb-3 flex items-center gap-2 font-display text-xl">
                <GraduationCap className="h-5 w-5 text-primary-600" aria-hidden="true" /> Materias
              </h2>
              <ul className="flex flex-wrap gap-2">
                {subjects.map((s, i) => (
                  <li key={`${s.subjectId}-${i}`}>
                    <Badge tone="neutral" className="px-3 py-1 text-sm">
                      {s.subjectName}
                      {s.careerName ? ` · ${s.careerName}` : ''}
                    </Badge>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {/* DISPONIBILIDAD */}
          <Card className="p-6">
            <h2 className="mb-3 flex items-center gap-2 font-display text-xl">
              <CalendarDays className="h-5 w-5 text-primary-600" aria-hidden="true" /> Disponibilidad semanal
            </h2>
            {teacher.availabilityNote && <p className="mb-3 text-sm text-content-muted">{teacher.availabilityNote}</p>}
            {availabilityByDay.length > 0 ? (
              <ul className="space-y-2">
                {availabilityByDay.map(([day, items]) => (
                  <li key={day} className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="w-24 font-semibold">{dayName(day)}</span>
                    {items.map((w) => (
                      <Badge key={w.id} tone="accent">
                        {w.startTime}–{w.endTime} · {dayPartLabel(w.dayPart)}
                      </Badge>
                    ))}
                  </li>
                ))}
              </ul>
            ) : (
              teacher.availabilityNote ? null : <p className="text-sm text-content-muted">Todavía no publicó horarios.</p>
            )}
          </Card>

          {/* FORMACIÓN */}
          {teacher.education && teacher.education.length > 0 && (
            <Card className="p-6">
              <h2 className="mb-3 font-display text-xl">Formación</h2>
              <ul className="space-y-2 text-sm">
                {teacher.education.map((e, i) => (
                  <li key={i}>
                    <span className="font-semibold">{e.degree}</span> — {e.institution}
                    {e.startYear ? ` (${e.startYear}${e.endYear ? `–${e.endYear}` : ''})` : ''}
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {/* OPINIONES */}
          <Card className="p-6">
            <h2 className="mb-3 font-display text-xl">Opiniones</h2>
            <p className="text-sm text-content-muted">
              Las opiniones estarán disponibles cuando la primera clase se concrete (próxima fase).
            </p>
          </Card>
        </div>

        {/* CTA sticky */}
        <aside className="lg:sticky lg:top-24 lg:h-fit">
          <Card className="space-y-4 p-6">
            <p className="text-3xl font-extrabold tracking-tight text-primary-700 dark:text-primary-200">{formatPriceHour(teacher.pricePerHour)}</p>
            <Button block size="lg" onClick={() => onContact(teacher)}>
              <MessageCircle className="h-5 w-5" aria-hidden="true" /> Contactar
            </Button>
            {onToggleFavorite && (
              <Button variant="outline" block onClick={() => onToggleFavorite(teacher)}>
                <Heart className={cn('h-4 w-4', favorite && 'fill-current text-error-600')} aria-hidden="true" />
                {favorite ? 'Guardado en favoritos' : 'Guardar en favoritos'}
              </Button>
            )}
            <p className="text-xs text-content-muted">
              El contacto no reserva la clase: coordinás día, hora y pago directamente con el profe.
            </p>
          </Card>
        </aside>
      </div>
    </div>
  );
}
