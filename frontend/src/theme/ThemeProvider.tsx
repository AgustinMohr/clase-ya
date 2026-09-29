import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

type Theme = 'light' | 'dark';

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
    // The transition class lives only while the theme changes: it animates the
    // variable-driven colors (surfaces, text, borders, brand blue) without touching
    // the timing of hover/state transitions elsewhere. First paint is unaffected
    // because the anti-flash script in index.html already set the class pre-render.
    root.classList.add(TRANSITION_CLASS);
    root.classList.toggle('dark', theme === 'dark');
    localStorage.setItem(STORAGE_KEY, theme);
    const timer = window.setTimeout(() => root.classList.remove(TRANSITION_CLASS), TRANSITION_MS);
    return () => {
      window.clearTimeout(timer);
      root.classList.remove(TRANSITION_CLASS);
    };
  }, [theme]);

  const setTheme = useCallback((next: Theme) => setThemeState(next), []);
  const toggle = useCallback(() => setThemeState((t) => (t === 'dark' ? 'light' : 'dark')), []);

  const value = useMemo(() => ({ theme, toggle, setTheme }), [theme, toggle, setTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx;
}
