import { useCallback, useEffect, useRef, useState } from 'react';
import { api, type Subject } from '../api';

/**
 * Landing/autocomplete data (REVIEW2-001).
 *
 * The app used to download the whole subject catalog on every visit — twice, before FE-01 — and
 * filtered it in the browser. Now it asks only for what it renders: `popular` for the landing
 * chips and a server-side query for the autocomplete, so a subject that is not among the popular
 * ones is still found (AC-5).
 */
export function useCatalog() {
  const [popular, setPopular] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const requestSeq = useRef(0);

  const reload = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setError(false);
    try {
      const page = await api.popularSubjects(12);
      if (seq === requestSeq.current) {
        setPopular(page.content);
      }
    } catch {
      if (seq === requestSeq.current) {
        setError(true);
      }
    } finally {
      if (seq === requestSeq.current) {
        setLoading(false);
      }
    }
  }, []);

  /** Debounced by the component that renders the dropdown, not here. */
  const search = useCallback(async (term: string): Promise<Subject[]> => {
    const trimmed = term.trim();
    if (!trimmed) return [];
    const page = await api.subjectSearch(trimmed, 10);
    return page.content;
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { popular, loading, error, reload, search };
}
