import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, FilterX, SearchX } from 'lucide-react';
import { api, type Subject, type TeacherSummary } from '../api';
import SearchInput from '../components/ui/SearchInput';
import { Button } from '../components/ui/Button';
import { Chip, EmptyState, Skeleton } from '../components/ui/primitives';
import { TeacherCard } from '../components/teacher/TeacherCard';

interface Props {
  initialTerm: string;
  subjects: Subject[];
  onBack: () => void;
  onOpenTeacher: (id: string) => void;
  onToggleFavorite?: (teacher: TeacherSummary) => void;
  favorites: Set<string>;
}

type Modality = 'ONLINE' | 'IN_PERSON';

export default function SearchPage({ initialTerm, subjects, onBack, onOpenTeacher, onToggleFavorite, favorites }: Props) {
  const [term, setTerm] = useState(initialTerm);
  const [subjectId, setSubjectId] = useState<string | null>(null);
  const [modality, setModality] = useState<Modality | null>(null);
  const [minRating, setMinRating] = useState<number | null>(null);
  const [priceMax, setPriceMax] = useState<number | null>(null);
  const [results, setResults] = useState<TeacherSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [unmatchedTerm, setUnmatchedTerm] = useState('');
  const requestSeq = useRef(0);

  const resolveSubject = useCallback(
    (value: string) => subjects.find((s) => s.name.toLowerCase() === value.trim().toLowerCase())?.id ?? null,
    [subjects],
  );

  const runSearch = useCallback(
    async (value: string, filters: { subjectId: string | null; modality: Modality | null; minRating: number | null }) => {
      const seq = ++requestSeq.current;
      setLoading(true);
      setError('');

      const trimmed = value.trim();
      let subjectToUse = filters.subjectId;
      if (trimmed && !subjectToUse) {
        subjectToUse = resolveSubject(trimmed);
        if (!subjectToUse) {
          setUnmatchedTerm(trimmed);
          setResults([]);
          setTotal(0);
          setLoading(false);
          return;
        }
      }
      setUnmatchedTerm('');
      setSubjectId(subjectToUse);

      try {
        const page = await api.teachers({
          subjectId: subjectToUse ?? undefined,
          modality: filters.modality ?? undefined,
          minRating: filters.minRating ?? undefined,
        });
        if (seq !== requestSeq.current) return; // stale response
        setResults(page.content);
        setTotal(page.totalElements);
      } catch {
        if (seq !== requestSeq.current) return;
        setError('No pudimos buscar profesores. Revisá tu conexión e intentá de nuevo.');
        setResults([]);
      } finally {
        if (seq === requestSeq.current) setLoading(false);
      }
    },
    [resolveSubject],
  );

  useEffect(() => {
    void runSearch(initialTerm, { subjectId: null, modality: null, minRating: null });
  }, [initialTerm, runSearch]);

  function applyFilters(next: { modality?: Modality | null; minRating?: number | null }) {
    const nextModality = next.modality !== undefined ? next.modality : modality;
    const nextRating = next.minRating !== undefined ? next.minRating : minRating;
    setModality(nextModality);
    setMinRating(nextRating);
    void runSearch(term, { subjectId, modality: nextModality, minRating: nextRating });
  }

  function clearFilters() {
    setModality(null);
    setMinRating(null);
    setPriceMax(null);
    void runSearch(term, { subjectId, modality: null, minRating: null });
  }

  const visible = useMemo(
    () => (priceMax == null ? results : results.filter((t) => t.pricePerHour == null || t.pricePerHour <= priceMax)),
    [results, priceMax],
  );

  const hasFilters = Boolean(subjectId || modality || minRating || priceMax);

  return (
    <div className="container-page py-8">
      <button
        type="button"
        onClick={onBack}
        className="mb-5 inline-flex items-center gap-1.5 text-sm font-semibold text-content-muted transition-colors hover:text-primary-600"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Volver al inicio
      </button>

      <div className="mb-6 max-w-3xl">
        <SearchInput
          value={term}
          onChange={setTerm}
          onSubmit={(value) => void runSearch(value, { subjectId: null, modality, minRating })}
          suggestions={subjects}
          popular={subjects}
          loading={loading}
        />
      </div>

      <div className="grid gap-8 lg:grid-cols-[260px_1fr]">
        {/* FILTROS */}
        <aside aria-label="Filtros" className="space-y-6 rounded-2xl border border-border bg-surface p-5 lg:sticky lg:top-24 lg:self-start">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold">Filtros</h2>
            {hasFilters && (
              <Button variant="link" size="sm" onClick={clearFilters}>
                <FilterX className="h-4 w-4" aria-hidden="true" /> Limpiar
              </Button>
            )}
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Modalidad</p>
            <div className="flex flex-wrap gap-2">
              <Chip active={modality === 'ONLINE'} onClick={() => applyFilters({ modality: modality === 'ONLINE' ? null : 'ONLINE' })}>
                Online
              </Chip>
              <Chip active={modality === 'IN_PERSON'} onClick={() => applyFilters({ modality: modality === 'IN_PERSON' ? null : 'IN_PERSON' })}>
                Presencial
              </Chip>
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Calificación</p>
            <div className="flex flex-wrap gap-2">
              {[4, 4.5].map((rating) => (
                <Chip key={rating} active={minRating === rating} onClick={() => applyFilters({ minRating: minRating === rating ? null : rating })}>
                  {rating}+ ★
                </Chip>
              ))}
            </div>
          </div>

          <div>
            <label htmlFor="price-max" className="mb-2 block text-xs font-semibold uppercase tracking-wide text-content-muted">
              Precio máximo (beta)
            </label>
            <select
              id="price-max"
              value={priceMax ?? ''}
              onChange={(e) => setPriceMax(e.target.value ? Number(e.target.value) : null)}
              className="w-full rounded-xl border border-border bg-surface px-3 py-2 text-sm"
            >
              <option value="">Sin límite</option>
              <option value="5000">Hasta $5.000/h</option>
              <option value="8000">Hasta $8.000/h</option>
              <option value="12000">Hasta $12.000/h</option>
            </select>
            <p className="mt-1 text-[11px] text-content-muted">El precio se filtra en el navegador (límite del backend actual).</p>
          </div>
        </aside>

        {/* RESULTADOS */}
        <section aria-live="polite">
          {loading ? (
            <div className="grid gap-5 sm:grid-cols-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-64" />
              ))}
            </div>
          ) : error ? (
            <EmptyState title="Algo salió mal" description={error} action={<Button onClick={() => runSearch(term, { subjectId, modality, minRating })}>Reintentar</Button>} />
          ) : unmatchedTerm ? (
            <EmptyState
              icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
              title={`No encontramos "${unmatchedTerm}"`}
              description="Probá eligiendo una materia de las sugerencias."
              action={<Button variant="outline" onClick={() => runSearch('', { subjectId: null, modality, minRating })}>Ver todos los profesores</Button>}
            />
          ) : visible.length === 0 ? (
            <EmptyState
              icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
              title="Sin resultados con estos filtros"
              description="Probá quitar algún filtro o buscar otra materia."
              action={<Button variant="outline" onClick={clearFilters}>Limpiar filtros</Button>}
            />
          ) : (
            <>
              <p className="mb-4 text-sm text-content-muted">{visible.length} profesor(es) encontrados</p>
              <div className="grid gap-5 sm:grid-cols-2">
                {visible.map((teacher) => (
                  <TeacherCard
                    key={teacher.id}
                    teacher={teacher}
                    onOpen={onOpenTeacher}
                    onToggleFavorite={onToggleFavorite}
                    favorite={favorites.has(teacher.id)}
                  />
                ))}
              </div>
              {total > visible.length && (
                <p className="mt-4 text-xs text-content-muted">Mostrando {visible.length} de {total} resultados.</p>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
