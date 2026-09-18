import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Search, TrendingUp, History } from 'lucide-react';
import { cn } from '../../lib/cn';
import { Button } from './Button';

export interface SearchSuggestion {
  id: string;
  name: string;
}

interface Props {
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  suggestions: SearchSuggestion[];
  popular?: SearchSuggestion[];
  loading?: boolean;
  placeholder?: string;
  size?: 'md' | 'lg';
}

const HISTORY_KEY = 'claseya.searchHistory';

function readHistory(): string[] {
  try {
    return JSON.parse(sessionStorage.getItem(HISTORY_KEY) ?? '[]');
  } catch {
    return [];
  }
}

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/** Search-first input with suggestions, recent history and popular subjects. */
export default function SearchInput({
  value,
  onChange,
  onSubmit,
  suggestions,
  popular = [],
  loading,
  placeholder = '¿Qué materia querés aprender?',
  size = 'lg',
}: Props) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const [history, setHistory] = useState<string[]>(readHistory);
  const rootRef = useRef<HTMLDivElement>(null);

  const filtered = useMemo(() => {
    const term = normalize(value);
    if (!term) return [];
    const matches = suggestions.filter((s) => normalize(s.name).includes(term));
    // Prefix matches first, then the rest.
    return matches.sort((a, b) => Number(!normalize(a.name).startsWith(term)) - Number(!normalize(b.name).startsWith(term))).slice(0, 6);
  }, [suggestions, value]);

  useEffect(() => {
    function onClickOutside(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  function submit(term: string) {
    const clean = term.trim();
    if (!clean) return;
    const next = [clean, ...history.filter((h) => h.toLowerCase() !== clean.toLowerCase())].slice(0, 5);
    setHistory(next);
    sessionStorage.setItem(HISTORY_KEY, JSON.stringify(next));
    setOpen(false);
    onSubmit(clean);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setHighlighted((h) => Math.min(h + 1, filtered.length - 1));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setHighlighted((h) => Math.max(h - 1, -1));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      submit(highlighted >= 0 && filtered[highlighted] ? filtered[highlighted].name : value);
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  }

  const renderOption = (option: SearchSuggestion, index: number) => (
    <li key={option.id} role="option" aria-selected={highlighted === index}>
      <button
        type="button"
        onMouseEnter={() => setHighlighted(index)}
        onClick={() => submit(option.name)}
        className={cn(
          'flex w-full items-center gap-2 px-4 py-2.5 text-left text-sm transition-colors',
          highlighted === index ? 'bg-surface-muted text-primary-700' : 'text-content hover:bg-surface-muted',
        )}
      >
        <Search className="h-3.5 w-3.5 text-content-muted" aria-hidden="true" />
        {option.name}
      </button>
    </li>
  );

  const showPanel = open && (filtered.length > 0 || history.length > 0 || popular.length > 0);

  return (
    <div ref={rootRef} className="relative w-full">
      <form
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          submit(value);
        }}
        className={cn(
          'flex items-center gap-2 rounded-2xl border border-border bg-surface p-2 shadow-card',
          size === 'lg' ? 'sm:p-2.5' : '',
        )}
      >
        <Search className="ml-2 h-5 w-5 shrink-0 text-content-muted" aria-hidden="true" />
        <input
          role="combobox"
          aria-expanded={showPanel}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-label="Buscar materia"
          className={cn(
            'min-w-0 flex-1 bg-transparent px-1 text-content placeholder:text-content-muted/70 focus:outline-none',
            size === 'lg' ? 'h-11 text-base sm:text-lg' : 'h-10 text-[15px]',
          )}
          placeholder={placeholder}
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
            setOpen(true);
            setHighlighted(-1);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        <Button type="submit" size={size === 'lg' ? 'lg' : 'md'} loading={loading} className="shrink-0">
          Buscar
        </Button>
      </form>

      {showPanel && (
        <div className="absolute z-30 mt-2 w-full overflow-hidden rounded-2xl border border-border bg-surface shadow-card-hover animate-fade-in">
          {filtered.length > 0 && (
            <ul id={listId} role="listbox" aria-label="Sugerencias" className="max-h-64 overflow-auto py-1">
              {filtered.map(renderOption)}
            </ul>
          )}

          {!value.trim() && history.length > 0 && (
            <div className="border-t border-border py-2">
              <p className="flex items-center gap-1.5 px-4 py-1 text-xs font-semibold uppercase tracking-wide text-content-muted">
                <History className="h-3.5 w-3.5" aria-hidden="true" /> Búsquedas recientes
              </p>
              <ul>
                {history.map((term, index) => renderOption({ id: `h-${index}`, name: term }, index))}
              </ul>
            </div>
          )}

          {!value.trim() && popular.length > 0 && (
            <div className="border-t border-border px-4 py-3">
              <p className="flex items-center gap-1.5 pb-2 text-xs font-semibold uppercase tracking-wide text-content-muted">
                <TrendingUp className="h-3.5 w-3.5" aria-hidden="true" /> Materias populares
              </p>
              <div className="flex flex-wrap gap-2">
                {popular.slice(0, 6).map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => submit(p.name)}
                    className="rounded-full border border-border px-3 py-1 text-sm transition-colors hover:border-primary-300 hover:text-primary-700"
                  >
                    {p.name}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
