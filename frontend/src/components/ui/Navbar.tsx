import { GraduationCap, Heart, LogOut, MessageCircle, Megaphone, Moon, Sun, User, UserRound } from 'lucide-react';
import { Link } from 'react-router';
import { useTheme } from '../../theme/ThemeProvider';
import { useAuth } from '../../auth/AuthContext';
import { Button, buttonStyles } from './Button';
import { ROUTES } from '../../routes';

const ROLE_LABELS: Record<string, string> = {
  STUDENT: 'Estudiante',
  TEACHER: 'Profesor',
  ADMIN: 'Administrador',
};

export function Navbar({
  onProfile,
  onLogin,
}: {
  onProfile: () => void;
  onLogin: () => void;
}) {
  const { theme, toggle } = useTheme();
  const { user, loading, logout } = useAuth();
  const isStudent = user?.role === 'STUDENT';
  const isTeacher = user?.role === 'TEACHER';

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-surface/85 backdrop-blur supports-[backdrop-filter]:bg-surface/70">
      <div className="container-page flex h-16 items-center justify-between gap-4">
        <Link
          to={ROUTES.home}
          className="flex items-center gap-2 rounded-lg px-1 py-1 text-left"
          aria-label="ClaseYa — inicio"
        >
          <span className="grid h-9 w-9 place-items-center rounded-xl bg-primary-600 text-white">
            <GraduationCap className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="font-display text-2xl leading-none text-primary-700 dark:text-primary-200">ClaseYa</span>
        </Link>

        <nav className="flex items-center gap-1.5" aria-label="Principal">
          {user && (
            <Link to={ROUTES.messages} className={buttonStyles({ variant: 'ghost', size: 'sm' })}>
              <MessageCircle className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Mensajes</span>
            </Link>
          )}
          {isStudent && (
            <Link to={ROUTES.favorites} className={buttonStyles({ variant: 'ghost', size: 'sm' })}>
              <Heart className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Favoritos</span>
            </Link>
          )}
          {isStudent && (
            <Button variant="ghost" size="sm" onClick={onProfile}>
              <User className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Mi perfil</span>
            </Button>
          )}
          {isTeacher && (
            <Link to={ROUTES.myListing} className={buttonStyles({ variant: 'ghost', size: 'sm' })}>
              <Megaphone className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Mi anuncio</span>
            </Link>
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
          {loading ? null : user ? (
            <div className="flex items-center gap-2">
              <span className="hidden items-center gap-1.5 text-sm font-semibold text-content-muted sm:flex">
                <UserRound className="h-4 w-4" aria-hidden="true" />
                {ROLE_LABELS[user.role] ?? 'Mi cuenta'}
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
