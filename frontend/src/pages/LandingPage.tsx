import { useEffect, useState } from 'react';
import { Compass, MessageCircle, Search, ShieldCheck, Sparkles, Users } from 'lucide-react';
import { api, type Subject, type TeacherSummary } from '../api';
import SearchInput from '../components/ui/SearchInput';
import { Section } from '../components/ui/Section';
import { Skeleton, Card } from '../components/ui/primitives';
import { CategoryCard } from '../components/teacher/CategoryCard';
import { TeacherCard } from '../components/teacher/TeacherCard';

interface Props {
  onSearch: (term: string) => void;
  onOpenTeacher: (id: string) => void;
  onToggleFavorite?: (teacher: TeacherSummary) => void;
  favorites: Set<string>;
}

const STEPS = [
  { icon: Search, title: 'Buscá por materia', text: 'Elegí la materia, modalidad y zona que necesitás.' },
  { icon: Users, title: 'Elegí tu profe', text: 'Mirá el perfil, las opiniones y el precio por hora.' },
  { icon: MessageCircle, title: 'Contactalo', text: 'Escribile sin exponer tus datos y coordiná la clase.' },
];

const BENEFITS = [
  { icon: ShieldCheck, title: 'Profes verificados', text: 'Docentes con identidad y formación validadas por ClaseYa.' },
  { icon: Compass, title: 'Sin vueltas', text: 'Encontrá y contactá en pocos clics. Sin reservas ni pagos dentro de la app.' },
  { icon: Sparkles, title: 'Precios claros', text: 'Compará precio por hora, modalidad y disponibilidad real.' },
];

export default function LandingPage({ onSearch, onOpenTeacher, onToggleFavorite, favorites }: Props) {
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [teachers, setTeachers] = useState<TeacherSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [heroQuery, setHeroQuery] = useState('');

  useEffect(() => {
    let active = true;
    Promise.all([api.subjects(), api.teachers()])
      .then(([subjectList, page]) => {
        if (!active) return;
        setSubjects(subjectList);
        setTeachers(page.content);
      })
      .catch(() => undefined)
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, []);

  const categories = subjects.slice(0, 8);
  const popular = subjects.slice(0, 6);

  return (
    <>
      {/* HERO */}
      <section className="relative overflow-hidden bg-gradient-to-b from-primary-600 via-primary-700 to-primary-800 text-white">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute -right-24 -top-24 h-64 w-64 rounded-full bg-secondary-500/30 blur-3xl"
        />
        <div className="container-page relative py-16 sm:py-24">
          <p className="mb-3 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-sm font-semibold">
            <Sparkles className="h-4 w-4" aria-hidden="true" /> Profesores cerca tuyo, en Santa Fe
          </p>
          <h1 className="max-w-3xl font-display text-4xl leading-tight sm:text-6xl">
            Encontrá tu profesor particular
          </h1>
          <p className="mt-4 max-w-2xl text-lg text-primary-50/90">
            Buscá por materia, compará profes verificados y contactalos gratis. Vos coordinás la clase.
          </p>

          <div className="mt-8 max-w-3xl">
            <SearchInput
              value={heroQuery}
              onChange={setHeroQuery}
              onSubmit={onSearch}
              suggestions={subjects}
              popular={popular}
              placeholder="¿Qué materia querés aprender? (ej. matemática, inglés, física…)"
            />
          </div>

          <div className="mt-6">
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-primary-100/80">
              Materias más buscadas
            </p>
            <div className="flex flex-wrap gap-2">
              {popular.map((subject) => (
                <button
                  key={subject.id}
                  type="button"
                  onClick={() => setHeroQuery(subject.name)}
                  className="rounded-full border border-white/25 bg-white/10 px-3.5 py-1.5 text-sm font-semibold transition-colors hover:bg-white/20 focus-visible:ring-white"
                >
                  {subject.name}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-primary-100/70">
              Tocá una materia para completar el buscador y presioná Buscar.
            </p>
          </div>
        </div>
      </section>

      {/* CATEGORÍAS */}
      <Section title="Materias populares" subtitle="Elegí una materia y mirá los profes disponibles.">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {loading
            ? Array.from({ length: 8 }).map((_, i) => <Skeleton key={i} className="h-[76px]" />)
            : categories.map((subject) => (
                <CategoryCard key={subject.id} name={subject.name} onClick={onSearch} />
              ))}
        </div>
      </Section>

      {/* PROFESORES DESTACADOS */}
      <Section
        title="Profesores destacados"
        subtitle="Perfiles verificados, con precio por hora y disponibilidad."
      >
        {loading ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-64" />
            ))}
          </div>
        ) : teachers.length > 0 ? (
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {teachers.slice(0, 6).map((teacher) => (
              <TeacherCard
                key={teacher.id}
                teacher={teacher}
                onOpen={onOpenTeacher}
                onToggleFavorite={onToggleFavorite}
                favorite={favorites.has(teacher.id)}
                featured
              />
            ))}
          </div>
        ) : (
          <Card className="p-6 text-center text-content-muted">
            Todavía no hay profesores publicados. Si sos profe, creá tu anuncio y aparecé acá.
          </Card>
        )}
      </Section>

      {/* CÓMO FUNCIONA */}
      <Section title="¿Cómo funciona?" subtitle="Tres pasos y estás en contacto con tu profe.">
        <ol className="grid gap-5 sm:grid-cols-3">
          {STEPS.map((step, index) => (
            <li key={step.title}>
              <Card className="h-full p-6">
                <span className="mb-3 inline-grid h-11 w-11 place-items-center rounded-xl bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200">
                  <step.icon className="h-5 w-5" aria-hidden="true" />
                </span>
                <h3 className="text-lg font-bold">
                  {index + 1}. {step.title}
                </h3>
                <p className="mt-1 text-sm text-content-muted">{step.text}</p>
              </Card>
            </li>
          ))}
        </ol>
      </Section>

      {/* BENEFICIOS */}
      <Section title="Por qué ClaseYa" subtitle="Pensado para estudiantes, familias y profesores.">
        <div className="grid gap-5 sm:grid-cols-3">
          {BENEFITS.map((benefit) => (
            <Card key={benefit.title} className="h-full p-6">
              <span className="mb-3 inline-grid h-11 w-11 place-items-center rounded-xl bg-secondary-100 text-secondary-700 dark:bg-secondary-700/25 dark:text-secondary-200">
                <benefit.icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <h3 className="text-lg font-bold">{benefit.title}</h3>
              <p className="mt-1 text-sm text-content-muted">{benefit.text}</p>
            </Card>
          ))}
        </div>
      </Section>
    </>
  );
}
