import { useCallback } from 'react';
import { useNavigate } from 'react-router';

/**
 * Goes back through the browser history only when the SPA has an entry to return to.
 * A deep-linked visit starts at idx 0, so it falls back to an in-app route instead of
 * leaving the site.
 */
export function useBack(fallback = '/') {
  const navigate = useNavigate();
  return useCallback(() => {
    const state = window.history.state as { idx?: number } | null;
    if (state?.idx && state.idx > 0) {
      navigate(-1);
      return;
    }
    navigate(fallback);
  }, [navigate, fallback]);
}
