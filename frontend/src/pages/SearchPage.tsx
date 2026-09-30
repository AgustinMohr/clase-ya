import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, ChevronLeft, ChevronRight, FilterX, SearchX } from 'lucide-react';
import { api, type Subject, type TeacherSummary } from '../api';
import SearchInput from '../components/ui/SearchInput';
import { Button } from '../components/ui/Button';
import { Chip, EmptyState, Skeleton } from '../components/ui/primitives';
import PriceRange, { type PriceValue } from '../components/ui/PriceRange';
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

interface Filters {
  subjectId: string | null;
  modality: Modality | null;
  minRating: number | null;
  price: PriceValue;
}

const NO_FILTERS: Filters = { subjectId: null, modality: null, minRating: null, price: { min: null, max: null } };

/** A typed pair can be inverted; order it before querying (SEARCH-001). */
function normalizePrice(price: PriceValue): PriceValue {
  const { min, max } = price;
  return min != null && max != null && min > max ? { min: max, max: min } : price;
}

function activeFilterCount(filters: Filters): number {
  return [filters.subjectId, filters.modality, filters.minRating, filters.price.min, filters.price.max]
    .filter((value) => value != null).length;
}

export default function SearchPage({ initialTerm, subjects, onBack, onOpenTeacher, onToggleFavorite, favorites }: Props) {
  const [term, setTerm] = useState(initialTerm);
  /** What the controls show. Changing it fires nothing. */
  const [draft, setDraft] = useState<Filters>(NO_FILTERS);
  /** What the results reflect. Only the explicit actions below update it. */
  const [applied, setApplied] = useState<Filters>(NO_FILTERS);
  const [results, setResults] = useState<TeacherSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [unmatchedTerm, setUnmatchedTerm] = useState('');
  // Skeletons are for the very first load only: replacing a full page of results with
  // skeletons collapses the document (the footer jumps into view) on every filter apply.
  const [hasSearched, setHasSearched] = useState(false);
  const requestSeq = useRef(0);

  const resolveSubject = useCallback(
    (value: string) => subjects.find((s) => s.name.toLowerCase() === value.trim().toLowerCase())?.id ?? null,
    [subjects],
  );

  const runSearch = useCallback(
    async (value: string, filters: Filters, nextPage = 0) => {
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
          setTotalPages(0);
          setLoading(false);
          return;
        }
      }
      setUnmatchedTerm('');
      const effective: Filters = { ...filters, subjectId: subjectToUse };
      setApplied(effective);

      try {
        const result = await api.teachers({
          subjectId: subjectToUse ?? undefined,
          modality: effective.modality ?? undefined,
          minRating: effective.minRating ?? undefined,
          minPrice: effective.price.min ?? undefined,
          maxPrice: effective.price.max ?? undefined,
          page: nextPage,
        });
        if (seq !== requestSeq.current) return; // stale response
        setResults(result.content);
        setTotal(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
        setHasSearched(true);
      } catch {
        if (seq !== requestSeq.current) return;
        setError('No pudimos buscar profesores. Revisá tu conexión e intentá de nuevo.');
        setResults([]);
        setTotal(0);
        setTotalPages(0);
      } finally {
        if (seq === requestSeq.current) setLoading(false);
      }
    },
    [resolveSubject],
  );

  /**
   * `runSearch` changes identity when the subject list loads, so the initial search
   * is keyed on the term only (a filter change must never trigger a request). The ref
   * also absorbs StrictMode's double effect invocation in development, which would
   * otherwise fire the same search twice on mount; it resets on remount, so landing
   * with the same term again still searches.
   */
  const runSearchRef = useRef(runSearch);
  useEffect(() => {
    runSearchRef.current = runSearch;
  }, [runSearch]);

  const initialTermSearched = useRef<string | null>(null);
  useEffect(() => {
    if (initialTermSearched.current === initialTerm) return;
    initialTermSearched.current = initialTerm;
    void runSearchRef.current(initialTerm, NO_FILTERS, 0);
  }, [initialTerm]);

  function updateDraft(next: Partial<Filters>) {
    setDraft((current) => ({ ...current, ...next }));
  }

  /** The only path that turns selected filters into a query (explicit user action). */
  function applyFilters() {
    const next: Filters = { ...draft, price: normalizePrice(draft.price) };
    setDraft(next);
    setApplied(next);
    void runSearch(term, next, 0);
  }

  function clearFilters() {
    setDraft(NO_FILTERS);
    setApplied(NO_FILTERS);
    void runSearch(term, NO_FILTERS, 0);
  }

  const selected = activeFilterCount(draft);

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
          onSubmit={(value) => void runSearch(value, { ...applied, subjectId: null }, 0)}
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
            {selected > 0 && (
              <Button variant="link" size="sm" onClick={clearFilters}>
                <FilterX className="h-4 w-4" aria-hidden="true" /> Limpiar
              </Button>
            )}
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Modalidad</p>
            <div className="flex flex-wrap gap-2">
              <Chip active={draft.modality === 'ONLINE'} onClick={() => updateDraft({ modality: draft.modality === 'ONLINE' ? null : 'ONLINE' })}>
                Online
              </Chip>
              <Chip active={draft.modality === 'IN_PERSON'} onClick={() => updateDraft({ modality: draft.modality === 'IN_PERSON' ? null : 'IN_PERSON' })}>
                Presencial
              </Chip>
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Calificación</p>
            <div className="flex flex-wrap gap-2">
              {[4, 4.5].map((rating) => (
                <Chip key={rating} active={draft.minRating === rating} onClick={() => updateDraft({ minRating: draft.minRating === rating ? null : rating })}>
                  {rating}+ ★
                </Chip>
              ))}
            </div>
          </div>

          <div>
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Precio por hora</p>
            <PriceRange value={draft.price} onChange={(price) => updateDraft({ price })} />
          </div>

          <div className="border-t border-border pt-4">
            <Button block onClick={applyFilters}>
              Aplicar filtros{selected > 0 ? ` (${selected})` : ''}
            </Button>
            <p className="mt-2 text-[11px] text-content-muted">
              Los filtros se aplican solo al tocar el botón.
            </p>
          </div>
        </aside>

        {/* RESULTADOS */}
        <section aria-live="polite" aria-busy={loading}>
          {loading && !hasSearched ? (
            <div className="grid gap-5 sm:grid-cols-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-64" />
              ))}
            </div>
          ) : error ? (
            <EmptyState title="Algo salió mal" description={error} action={<Button onClick={() => runSearch(term, applied, page)}>Reintentar</Button>} />
          ) : unmatchedTerm ? (
            <EmptyState
              icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
              title={`No encontramos "${unmatchedTerm}"`}
              description="Probá eligiendo una materia de las sugerencias."
              action={
                <Button
                  variant="outline"
                  onClick={() => {
                    // Clear the typed term too: otherwise paging or applying a filter re-runs the
                    // search with the unmatched text and the results disappear.
                    setTerm('');
                    void runSearch('', { ...applied, subjectId: null }, 0);
                  }}
                >
                  Ver todos los profesores
                </Button>
              }
            />
          ) : results.length === 0 ? (
            <EmptyState
              icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
              title="Sin resultados con estos filtros"
              description="Probá quitar algún filtro o buscar otra materia."
              action={<Button variant="outline" onClick={clearFilters}>Limpiar filtros</Button>}
            />
          ) : (
            <div className={loading ? 'opacity-50 transition-opacity duration-200' : 'transition-opacity duration-200'}>
              <p className="mb-4 text-sm text-content-muted">
                {total} profesor{total === 1 ? '' : 'es'} encontrado{total === 1 ? '' : 's'}
              </p>
              <div className="grid gap-5 sm:grid-cols-2">
                {results.map((teacher) => (
                  <TeacherCard
                    key={teacher.id}
                    teacher={teacher}
                    onOpen={onOpenTeacher}
                    onToggleFavorite={onToggleFavorite}
                    favorite={favorites.has(teacher.id)}
                  />
                ))}
              </div>

              {totalPages > 1 && (
                <nav className="mt-6 flex items-center justify-between gap-3" aria-label="Paginación de resultados">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page === 0 || loading}
                    onClick={() => void runSearch(term, applied, page - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior
                  </Button>
                  <p className="text-sm text-content-muted">Página {page + 1} de {totalPages}</p>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= totalPages || loading}
                    onClick={() => void runSearch(term, applied, page + 1)}
                  >
                    Siguiente <ChevronRight className="h-4 w-4" aria-hidden="true" />
                  </Button>
                </nav>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
