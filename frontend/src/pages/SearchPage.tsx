import { useCallback, useEffect, useRef, useState } from 'react';
import { ArrowLeft, ChevronLeft, ChevronRight, FilterX, SearchX } from 'lucide-react';
import { useSearchParams } from 'react-router';
import { api, type Subject, type TeacherSummary } from '../api';
import { usePageMeta } from '../hooks/usePageMeta';
import SearchInput from '../components/ui/SearchInput';
import { Button } from '../components/ui/Button';
import { Chip, EmptyState, Skeleton } from '../components/ui/primitives';
import PriceRange, { type PriceValue } from '../components/ui/PriceRange';
import { TeacherCard } from '../components/teacher/TeacherCard';

interface Props {
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
  onlyVerified: boolean;
}

interface SearchState {
  term: string;
  filters: Filters;
  page: number;
}

const NO_FILTERS: Filters = { subjectId: null, modality: null, minRating: null, price: { min: null, max: null }, onlyVerified: false };

/** A typed pair can be inverted; order it before querying (SEARCH-001). */
function normalizePrice(price: PriceValue): PriceValue {
  const { min, max } = price;
  return min != null && max != null && min > max ? { min: max, max: min } : price;
}

function activeFilterCount(filters: Filters): number {
  return [filters.subjectId, filters.modality, filters.minRating, filters.price.min, filters.price.max]
    .filter((value) => value != null).length + (filters.onlyVerified ? 1 : 0);
}

function parseNonNegativeInt(value: string | null): number | null {
  if (value == null || value.trim() === '') return null;
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 ? number : null;
}

/** Reads and validates the whitelisted query params. Anything unknown or invalid is ignored. */
function parseSearch(params: URLSearchParams): SearchState {
  const term = (params.get('materia') ?? '').trim();
  const modalityParam = params.get('modalidad');
  const modality = modalityParam === 'ONLINE' || modalityParam === 'IN_PERSON' ? modalityParam : null;
  const rating = Number(params.get('rating'));
  const minRating = rating === 4 || rating === 4.5 ? rating : null;
  const pageParam = Number(params.get('pagina'));
  const page = Number.isInteger(pageParam) && pageParam >= 1 ? pageParam - 1 : 0;

  return {
    term,
    page,
    filters: {
      subjectId: null,
      modality,
      minRating,
      price: { min: parseNonNegativeInt(params.get('precioMin')), max: parseNonNegativeInt(params.get('precioMax')) },
      onlyVerified: params.get('verificados') === 'true',
    },
  };
}

/** Only known, non-empty params are serialized: the URL stays readable and shareable. */
function buildSearch(term: string, filters: Filters, page: number): URLSearchParams {
  const params = new URLSearchParams();
  const trimmed = term.trim();
  if (trimmed) params.set('materia', trimmed);
  if (filters.modality) params.set('modalidad', filters.modality);
  if (filters.minRating != null) params.set('rating', String(filters.minRating));
  if (filters.price.min != null) params.set('precioMin', String(filters.price.min));
  if (filters.price.max != null) params.set('precioMax', String(filters.price.max));
  if (filters.onlyVerified) params.set('verificados', 'true');
  if (page > 0) params.set('pagina', String(page + 1));
  return params;
}

export default function SearchPage({ subjects, onBack, onOpenTeacher, onToggleFavorite, favorites }: Props) {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryKey = searchParams.toString();
  const urlTerm = (searchParams.get('materia') ?? '').trim();

  const initial = useRef<SearchState>(parseSearch(searchParams));
  const [term, setTerm] = useState(initial.current.term);
  /** What the controls show. Changing it fires nothing. */
  const [draft, setDraft] = useState<Filters>(initial.current.filters);
  /** What the results reflect. Only the explicit actions below update it. */
  const [applied, setApplied] = useState<Filters>(initial.current.filters);
  const [results, setResults] = useState<TeacherSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(initial.current.page);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [unmatchedTerm, setUnmatchedTerm] = useState('');
  // Skeletons are for the very first load only: replacing a full page of results with
  // skeletons collapses the document (the footer jumps into view) on every filter apply.
  const [hasSearched, setHasSearched] = useState(false);
  const requestSeq = useRef(0);
  // A term cannot be resolved before the subject catalog arrives; remember it and retry once.
  const pendingTerm = useRef<string | null>(null);

  usePageMeta({
    title: urlTerm ? `Profesores de ${urlTerm} | ClaseYa` : 'Buscar profesores particulares | ClaseYa',
    description: urlTerm
      ? `Encontrá y compará profesores de ${urlTerm} por precio, modalidad y disponibilidad. Contactalos gratis.`
      : 'Buscá profesores particulares por materia, modalidad, precio y calificación. Contactalos gratis.',
  });

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
          // The catalog has not loaded yet: wait instead of reporting a false "not found".
          if (subjects.length === 0) {
            pendingTerm.current = trimmed;
            return;
          }
          pendingTerm.current = null;
          setUnmatchedTerm(trimmed);
          setResults([]);
          setTotal(0);
          setTotalPages(0);
          setLoading(false);
          return;
        }
      }
      pendingTerm.current = null;
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
          onlyVerified: effective.onlyVerified,
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
    [resolveSubject, subjects.length],
  );

  /**
   * `runSearch` changes identity when the subject list loads, so the URL effect calls it through
   * a ref. The ref also absorbs StrictMode's double effect invocation in development.
   */
  const runSearchRef = useRef(runSearch);
  useEffect(() => {
    runSearchRef.current = runSearch;
  }, [runSearch]);

  // The URL is the source of truth: direct entry, back/forward and the actions below all land here.
  const lastQueryKey = useRef<string | null>(null);
  useEffect(() => {
    if (lastQueryKey.current === queryKey) return;
    lastQueryKey.current = queryKey;
    const next = parseSearch(searchParams);
    setTerm(next.term);
    setDraft(next.filters);
    setApplied(next.filters);
    // `page` is only updated when the response arrives: the indicator must never claim a
    // page whose results are not on screen yet.
    void runSearchRef.current(next.term, next.filters, next.page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryKey]);

  // A deep link may arrive before the catalog: run the pending term as soon as it is available.
  useEffect(() => {
    if (subjects.length === 0 || !pendingTerm.current) return;
    pendingTerm.current = null;
    const next = parseSearch(searchParams);
    void runSearchRef.current(next.term, next.filters, next.page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subjects.length]);

  function updateDraft(next: Partial<Filters>) {
    setDraft((current) => ({ ...current, ...next }));
  }

  /** The only path that turns the draft into a query and a new URL (explicit user action). */
  function applyFilters() {
    const next: Filters = { ...draft, price: normalizePrice(draft.price) };
    setDraft(next);
    setSearchParams(buildSearch(term, next, 0));
  }

  function clearFilters() {
    setDraft(NO_FILTERS);
    setSearchParams(buildSearch(term, NO_FILTERS, 0));
  }

  function submitTerm(value: string) {
    setTerm(value);
    setSearchParams(buildSearch(value, { ...applied, subjectId: null }, 0));
  }

  function goToPage(nextPage: number) {
    setSearchParams(buildSearch(term, applied, nextPage));
  }

  const selected = activeFilterCount(draft);

  return (
    <div className="container-page py-8" data-testid="search-page">
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
          onSubmit={submitTerm}
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
            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">Verificación</p>
            <Chip active={draft.onlyVerified} onClick={() => updateDraft({ onlyVerified: !draft.onlyVerified })}>
              Solo verificados
            </Chip>
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
                    setSearchParams(buildSearch('', { ...applied, subjectId: null }, 0));
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
                    onClick={() => goToPage(page - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior
                  </Button>
                  <p className="text-sm text-content-muted">Página {page + 1} de {totalPages}</p>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= totalPages || loading}
                    onClick={() => goToPage(page + 1)}
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
