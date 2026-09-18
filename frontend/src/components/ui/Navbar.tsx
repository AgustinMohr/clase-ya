import { GraduationCap, Heart, LogOut, Moon, Sun, UserRound } from 'lucide-react';
import { useTheme } from '../../theme/ThemeProvider';
import { useAuth } from '../../auth/AuthContext';
import { Button } from './Button';

export function Navbar({
  onHome,
  onFavorites,
  onLogin,
}: {
  onHome: () => void;
  onFavorites: () => void;
  onLogin: () => void;
}) {
  const { theme, toggle } = useTheme();
  const { user, logout } = useAuth();

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface/85 backdrop-blur supports-[backdrop-filter]:bg-surface/70">
      <div className="container-page flex h-16 items-center justify-between gap-4">
        <button
          type="button"
          onClick={onHome}
          className="flex items-center gap-2 rounded-lg px-1 py-1 text-left"
          aria-label="ClaseYa — inicio"
        >
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-primary-600 text-white">
            <GraduationCap className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="font-display text-2xl leading-none text-primary-700 dark:text-primary-200">ClaseYa</span>
        </button>

        <nav className="flex items-center gap-1.5" aria-label="Principal">
          {user && (
            <Button variant="ghost" size="sm" onClick={onFavorites}>
              <Heart className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Favoritos</span>
            </Button>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={toggle}
            aria-label={theme === 'dark' ? 'Activar modo claro' : 'Activar modo oscuro'}
            title={theme === 'dark' ? 'Modo claro' : 'Modo oscuro'}
          >
            {theme === 'dark' ? <Sun className="h-5 w-5" aria-hidden="true" /> : <Moon className="h-5 w-5" aria-hidden="true" />}
          </Button>
          {user ? (
            <div className="flex items-center gap-2">
              <span className="hidden items-center gap-1.5 text-sm font-semibold text-content-muted sm:flex">
                <UserRound className="h-4 w-4" aria-hidden="true" />
                {user.name || user.email}
              </span>
              <Button variant="outline" size="sm" onClick={logout}>
                <LogOut className="h-4 w-4" aria-hidden="true" />
                <span className="hidden sm:inline">Salir</span>
              </Button>
            </div>
          ) : (
            <Button size="sm" onClick={onLogin}>
              Ingresar
            </Button>
          )}
        </nav>
      </div>
    </header>
  );
}
