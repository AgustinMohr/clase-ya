import { useEffect, useId, useRef, useState } from 'react';
import { Search, X } from 'lucide-react';
import type { Subject } from '../../api';
import { cn } from '../../lib/cn';

interface Props {
  id: string;
  loadSubjects: (term: string) => Promise<Subject[]>;
  onSelect: (subject: Subject) => void | Promise<void>;
  placeholder?: string;
  disabled?: boolean;
}

const DEBOUNCE_MS = 250;
const MIN_CHARS = 2;

export default function SubjectCombobox({
  id,
  loadSubjects,
  onSelect,
  placeholder = 'Ej. Matemática, Inglés, Apoyo escolar',
  disabled,
}: Props) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const requestSeq = useRef(0);
  const [term, setTerm] = useState('');
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<Subject[]>([]);
  const [highlighted, setHighlighted] = useState(-1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const trimmed = term.trim();
    if (trimmed.length < MIN_CHARS) {
      setResults([]);
      setLoading(false);
      setError('');
      return;
    }

    const seq = ++requestSeq.current;
    setLoading(true);
    setError('');
    const timer = window.setTimeout(() => {
      loadSubjects(trimmed)
        .then((list) => {
          if (seq !== requestSeq.current) return;
          setResults(list);
          setHighlighted(list.length > 0 ? 0 : -1);
          if (list.length === 0) {
            setError('No encontramos materias con ese nombre. Probá con otra palabra.');
          }
        })
        .catch(() => {
          if (seq === requestSeq.current) {
            setResults([]);
            setError('No pudimos buscar materias. Revisá tu conexión e intentá de nuevo.');
          }
        })
        .finally(() => {
          if (seq === requestSeq.current) setLoading(false);
        });
    }, DEBOUNCE_MS);

    return () => window.clearTimeout(timer);
  }, [term, loadSubjects]);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  async function select(subject: Subject) {
    setTerm('');
    setResults([]);
    setError('');
    setOpen(false);
    await onSelect(subject);
    inputRef.current?.focus();
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlighted((current) => Math.min(current + 1, results.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((current) => Math.max(current - 1, -1));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const subject = results[highlighted];
      if (subject) void select(subject);
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  const showPanel = open && term.trim().length >= MIN_CHARS;

  return (
    <div ref={rootRef} className="relative">
      <div className="flex items-center gap-2 rounded-xl border border-border bg-surface px-3 focus-within:border-primary-500 focus-within:ring-2 focus-within:ring-ring/40">
        <Search className="h-4 w-4 shrink-0 text-content-muted" aria-hidden="true" />
        <input
          ref={inputRef}
          id={id}
          role="combobox"
          aria-expanded={showPanel}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-label="Buscar materia"
          value={term}
          onChange={(event) => {
            setTerm(event.target.value);
            setOpen(true);
            setHighlighted(-1);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder={placeholder}
          maxLength={255}
          disabled={disabled}
          className="h-11 min-w-0 flex-1 bg-transparent text-sm text-content placeholder:text-content-muted/70 focus:outline-none disabled:opacity-50"
        />
        {term && (
          <button
            type="button"
            aria-label="Limpiar búsqueda"
            onClick={() => {
              setTerm('');
              setResults([]);
              setError('');
              inputRef.current?.focus();
            }}
            className="rounded-full p-1 text-content-muted hover:text-content"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>

      {showPanel && (
        <div className="absolute z-30 mt-2 w-full overflow-hidden rounded-xl border border-border bg-surface shadow-card-hover">
          {loading && <p role="status" className="px-4 py-3 text-sm text-content-muted">Buscando materias…</p>}
          {!loading && error && <p role="alert" className="px-4 py-3 text-sm font-semibold text-error-600">{error}</p>}
          {!loading && results.length > 0 && (
            <ul id={listId} role="listbox" aria-label="Materias" className="max-h-64 overflow-auto py-1">
              {results.map((subject, index) => (
                <li key={subject.id} role="option" aria-selected={highlighted === index}>
                  <button
                    type="button"
                    onMouseEnter={() => setHighlighted(index)}
                    onClick={() => void select(subject)}
                    className={cn(
                      'flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm transition-colors',
                      highlighted === index ? 'bg-surface-muted text-primary-700' : 'text-content hover:bg-surface-muted',
                    )}
                  >
                    <Search className="h-3.5 w-3.5 text-content-muted" aria-hidden="true" />
                    {subject.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
