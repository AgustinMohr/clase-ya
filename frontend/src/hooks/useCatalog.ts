import { useCallback, useEffect, useRef, useState } from 'react';
import { api, type Subject } from '../api';

/**
 * Loads the subject catalog once for the whole app. The landing used to request it again on top
 * of `App`, so every visit downloaded the (unpaginated) catalog twice and both failures were
 * swallowed (FE-01). One hook, one request, one error state with a retry.
 */
export function useCatalog() {
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const requestSeq = useRef(0);

  const reload = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setError(false);
    try {
      const list = await api.subjects();
      if (seq === requestSeq.current) {
        setSubjects(list);
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

  useEffect(() => {
    void reload();
  }, [reload]);

  return { subjects, loading, error, reload };
}
