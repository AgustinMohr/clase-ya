import { useEffect, useState } from 'react';
import { Heart } from 'lucide-react';
import { api, ApiError, type Subject, type TeacherSummary } from './api';
import { useAuth } from './auth/AuthContext';
import { useToast } from './components/ui/Toast';
import { Navbar } from './components/ui/Navbar';
import { Footer } from './components/ui/Footer';
import { Button } from './components/ui/Button';
import { EmptyState, Skeleton } from './components/ui/primitives';
import { TeacherCard } from './components/teacher/TeacherCard';
import LandingPage from './pages/LandingPage';
import SearchPage from './pages/SearchPage';
import TeacherProfilePage from './pages/TeacherProfilePage';
import LoginModal from './components/LoginModal';
import ContactModal from './components/ContactModal';

type View =
  | { name: 'landing' }
  | { name: 'search'; term: string }
  | { name: 'profile'; id: string }
  | { name: 'favorites' };

export default function App() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [view, setView] = useState<View>({ name: 'landing' });
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [favoriteTeachers, setFavoriteTeachers] = useState<TeacherSummary[]>([]);
  const [favoritesLoading, setFavoritesLoading] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [pendingContact, setPendingContact] = useState<TeacherSummary | null>(null);
  const [contactTarget, setContactTarget] = useState<TeacherSummary | null>(null);

  useEffect(() => {
    api.subjects().then(setSubjects).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!user) {
      setFavorites(new Set());
      setFavoriteTeachers([]);
      return;
    }
    api
      .favorites()
      .then((page) => {
        setFavoriteTeachers(page.content.map((f) => f.teacher));
        setFavorites(new Set(page.content.map((f) => f.teacher.id)));
      })
      .catch(() => undefined);
  }, [user]);

  async function toggleFavorite(teacher: TeacherSummary) {
    if (!user) {
      setPendingContact(null);
      setLoginOpen(true);
      return;
    }
    const isFavorite = favorites.has(teacher.id);
    try {
      if (isFavorite) {
        await api.removeFavorite(teacher.id);
        setFavorites((prev) => {
          const next = new Set(prev);
          next.delete(teacher.id);
          return next;
        });
        setFavoriteTeachers((prev) => prev.filter((t) => t.id !== teacher.id));
        notify('Quitado de favoritos');
      } else {
        await api.addFavorite(teacher.id);
        setFavorites((prev) => new Set(prev).add(teacher.id));
        setFavoriteTeachers((prev) => [teacher, ...prev.filter((t) => t.id !== teacher.id)]);
        notify('Guardado en favoritos');
      }
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 409
          ? 'Completá tu perfil de estudiante para guardar favoritos.'
          : 'No pudimos actualizar tus favoritos.';
      notify(message, 'error');
    }
  }

  function contact(teacher: TeacherSummary) {
    if (!user) {
      setPendingContact(teacher);
      setLoginOpen(true);
      return;
    }
    setContactTarget(teacher);
  }

  function openFavorites() {
    if (!user) {
      setLoginOpen(true);
      return;
    }
    setFavoritesLoading(true);
    api
      .favorites()
      .then((page) => setFavoriteTeachers(page.content.map((f) => f.teacher)))
      .catch(() => notify('No pudimos cargar tus favoritos.', 'error'))
      .finally(() => setFavoritesLoading(false));
    setView({ name: 'favorites' });
  }

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar onHome={() => setView({ name: 'landing' })} onFavorites={openFavorites} onLogin={() => setLoginOpen(true)} />

      <main className="flex-1">
        {view.name === 'landing' && (
          <LandingPage
            onSearch={(term) => setView({ name: 'search', term })}
            onOpenTeacher={(id) => setView({ name: 'profile', id })}
            onToggleFavorite={toggleFavorite}
            favorites={favorites}
          />
        )}

        {view.name === 'search' && (
          <SearchPage
            initialTerm={view.term}
            subjects={subjects}
            onBack={() => setView({ name: 'landing' })}
            onOpenTeacher={(id) => setView({ name: 'profile', id })}
            onToggleFavorite={toggleFavorite}
            favorites={favorites}
          />
        )}

        {view.name === 'profile' && (
          <TeacherProfilePage
            teacherId={view.id}
            onBack={() => setView({ name: 'landing' })}
            onContact={contact}
            onToggleFavorite={toggleFavorite}
            favorite={favorites.has(view.id)}
          />
        )}

        {view.name === 'favorites' && (
          <div className="container-page py-8">
            <h1 className="mb-6 font-display text-3xl">Mis favoritos</h1>
            {favoritesLoading ? (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-64" />
                ))}
              </div>
            ) : favoriteTeachers.length === 0 ? (
              <EmptyState
                icon={<Heart className="h-6 w-6" aria-hidden="true" />}
                title="Todavía no guardaste profesores"
                description="Explorá el buscador y guardá los perfiles que te interesen."
                action={<Button onClick={() => setView({ name: 'landing' })}>Buscar profesores</Button>}
              />
            ) : (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {favoriteTeachers.map((teacher) => (
                  <TeacherCard
                    key={teacher.id}
                    teacher={teacher}
                    onOpen={(id) => setView({ name: 'profile', id })}
                    onToggleFavorite={toggleFavorite}
                    favorite
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      <Footer onNavigate={() => setView({ name: 'landing' })} />

      <LoginModal
        open={loginOpen}
        onClose={() => {
          setLoginOpen(false);
          setPendingContact(null);
        }}
        onSuccess={() => {
          setLoginOpen(false);
          if (pendingContact) {
            setContactTarget(pendingContact);
            setPendingContact(null);
          }
        }}
      />
      <ContactModal
        open={contactTarget !== null}
        teacherName={contactTarget?.displayName ?? ''}
        onClose={() => setContactTarget(null)}
      />
    </div>
  );
}
