import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';

type Theme = 'light' | 'dark';

interface ViewTransitionDocument extends Document {
  startViewTransition?: (callback: () => void) => { finished: Promise<void> };
}

interface ThemeContextValue {
  theme: Theme;
  toggle: () => void;
  setTheme: (theme: Theme) => void;
}

const STORAGE_KEY = 'claseya.theme';
/** Class defined in `index.css` that animates the color swap; see the effect below. */
const TRANSITION_CLASS = 'theme-transition';
const TRANSITION_MS = 200;
const ThemeContext = createContext<ThemeContextValue | null>(null);

function readInitialTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') return stored;
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => readInitialTheme());

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', theme === 'dark');
    localStorage.setItem(STORAGE_KEY, theme);

    if (prefersReducedMotion() || supportsViewTransitions()) {
      root.classList.remove(TRANSITION_CLASS);
      return;
    }

    root.classList.add(TRANSITION_CLASS);
    const timer = window.setTimeout(() => root.classList.remove(TRANSITION_CLASS), TRANSITION_MS);
    return () => {
      window.clearTimeout(timer);
      root.classList.remove(TRANSITION_CLASS);
    };
  }, [theme]);

  const applyTheme = useCallback((next: Theme) => {
    if (prefersReducedMotion() || !supportsViewTransitions()) {
      setThemeState(next);
      return;
    }
    (document as ViewTransitionDocument).startViewTransition?.(() => {
      flushSync(() => setThemeState(next));
    });
  }, []);

  const setTheme = useCallback((next: Theme) => applyTheme(next), [applyTheme]);
  const toggle = useCallback(() => applyTheme(theme === 'dark' ? 'light' : 'dark'), [applyTheme, theme]);

  const value = useMemo(() => ({ theme, toggle, setTheme }), [theme, toggle, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function supportsViewTransitions(): boolean {
  return typeof (document as ViewTransitionDocument).startViewTransition === 'function';
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
