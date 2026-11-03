import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';

interface Props {
  /** 0-based page index, matching the API and SearchPage's own state. */
  page: number;
  totalPages: number;
  loading?: boolean;
  onChange: (page: number) => void;
  label?: string;
}

/**
 * Previous/next pagination shared by the search results and the admin directory (ADMIN-002).
 * It renders nothing when there is a single page, so callers never show a dead control.
 */
export function Pagination({ page, totalPages, loading = false, onChange, label = 'Paginación' }: Props) {
  if (totalPages <= 1) return null;

  return (
    <nav className="mt-6 flex items-center justify-between gap-3" aria-label={label}>
      <Button
        variant="outline"
        size="sm"
        disabled={page === 0 || loading}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft className="h-4 w-4" aria-hidden="true" /> Anterior
      </Button>
      <p className="text-sm text-content-muted">
        Página {page + 1} de {totalPages}
      </p>
      <Button
        variant="outline"
        size="sm"
        disabled={page + 1 >= totalPages || loading}
        onClick={() => onChange(page + 1)}
      >
        Siguiente <ChevronRight className="h-4 w-4" aria-hidden="true" />
      </Button>
    </nav>
  );
}
