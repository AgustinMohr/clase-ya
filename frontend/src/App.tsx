import { useCallback, useEffect, useState } from 'react';
import { Heart } from 'lucide-react';
import { api, ApiError, type StudentProfile, type Subject, type TeacherSummary } from './api';
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
import MessagesPage from './pages/MessagesPage';
import MyListingPage from './pages/MyListingPage';
import LoginModal from './components/LoginModal';
import ContactModal from './components/ContactModal';
import StudentProfileModal from './components/StudentProfileModal';

type View =
  | { name: 'landing' }
  | { name: 'search'; term: string }
  | { name: 'profile'; id: string }
  | { name: 'favorites' }
  | { name: 'my-listing' }
  | { name: 'messages'; conversationId?: string };

const LANDING_VIEW: View = { name: 'landing' };

export default function App() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [view, setView] = useState<View>(LANDING_VIEW);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [favoriteTeachers, setFavoriteTeachers] = useState<TeacherSummary[]>([]);
  const [favoritesLoading, setFavoritesLoading] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [pendingContact, setPendingContact] = useState<TeacherSummary | null>(null);
  const [contactTarget, setContactTarget] = useState<TeacherSummary | null>(null);
  // Student profile: null means it does not exist yet, which blocks both contacting
  // a teacher and saving favorites (the API answers 409). CONTACT-001 RF-1.
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [profileChecked, setProfileChecked] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  // The in-app view is kept in sync with the browser history so the back/forward buttons move
  // between screens instead of leaving the app. There is no router yet, so the entry only stores
  // the view state and the URL stays put.
  const navigate = useCallback((next: View) => {
    setView(next);
    window.history.pushState({ view: next }, '');
  }, []);

  useEffect(() => {
    if (!window.history.state?.view) {
      window.history.replaceState({ view: LANDING_VIEW }, '');
    }
    const onPopState = (event: PopStateEvent) => {
      const restored = (event.state as { view?: View } | null)?.view;
      setView(restored ?? LANDING_VIEW);
    };
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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

  useEffect(() => {
    if (!user) {
      setProfile(null);
      setProfileChecked(false);
      return;
    }
    if (user.role !== 'STUDENT') {
      setProfile(null);
      setProfileChecked(true);
      return;
    }
    api
      .studentProfile()
      .then(setProfile)
      .catch(() => setProfile(null))
      .finally(() => setProfileChecked(true));
  }, [user]);

  // Logging out (or an expired session) must not keep showing private views.
  useEffect(() => {
    if (!user) {
      setView(LANDING_VIEW);
      window.history.replaceState({ view: LANDING_VIEW }, '');
    }
  }, [user]);

  // Every view change starts at the top: arriving at a teacher profile from a scrolled
  // result list used to land in the middle of the page.
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0 });
  }, [view]);

  const needsProfile = Boolean(user && user.role === 'STUDENT' && profileChecked && !profile);

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
      if (error instanceof ApiError && error.status === 409) {
        notify('Completá tu perfil de estudiante para guardar favoritos.', 'error');
        setProfileOpen(true);
        return;
      }
      notify('No pudimos actualizar tus favoritos.', 'error');
    }
  }

  /** Opens the student profile form, remembering the teacher the user wanted to contact. */
  function openProfileForm(teacher: TeacherSummary | null) {
    if (teacher) {
      setPendingContact(teacher);
    }
    setContactTarget(null);
    setProfileOpen(true);
  }

  function contact(teacher: TeacherSummary) {
    if (!user) {
      setPendingContact(teacher);
      setLoginOpen(true);
      return;
    }
    // Ask for the profile before the API has to reject the contact with a 409.
    if (needsProfile) {
      openProfileForm(teacher);
      return;
    }
    setContactTarget(teacher);
  }

  function handleProfileSaved(saved: StudentProfile) {
    setProfile(saved);
    setProfileChecked(true);
    setProfileOpen(false);
    notify('Perfil completado: ya podés contactar y guardar favoritos');
    if (pendingContact) {
      setContactTarget(pendingContact);
      setPendingContact(null);
    }
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
    navigate({ name: 'favorites' });
  }

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar
        onHome={() => navigate({ name: 'landing' })}
        onFavorites={openFavorites}
        onMessages={() => navigate({ name: 'messages' })}
        onProfile={() => setProfileOpen(true)}
        onMyListing={() => navigate({ name: 'my-listing' })}
        onLogin={() => setLoginOpen(true)}
      />

      <main className="flex-1">
        {view.name === 'landing' && (
          <LandingPage
            onSearch={(term) => navigate({ name: 'search', term })}
            onOpenTeacher={(id) => navigate({ name: 'profile', id })}
            onToggleFavorite={toggleFavorite}
            favorites={favorites}
          />
        )}

        {view.name === 'search' && (
          <SearchPage
            initialTerm={view.term}
            subjects={subjects}
            onBack={() => navigate({ name: 'landing' })}
            onOpenTeacher={(id) => navigate({ name: 'profile', id })}
            onToggleFavorite={toggleFavorite}
            favorites={favorites}
          />
        )}

        {view.name === 'profile' && (
          <TeacherProfilePage
            teacherId={view.id}
            onBack={() => navigate({ name: 'landing' })}
            onContact={contact}
            onToggleFavorite={user && user.role !== 'STUDENT' ? undefined : toggleFavorite}
            favorite={favorites.has(view.id)}
          />
        )}

        {view.name === 'messages' && (
          <MessagesPage
            onBack={() => navigate({ name: 'landing' })}
            initialConversationId={view.conversationId}
          />
        )}

        {view.name === 'my-listing' && (
          <MyListingPage
            onBack={() => navigate({ name: 'landing' })}
            onPreview={(id) => navigate({ name: 'profile', id })}
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
                action={<Button onClick={() => navigate({ name: 'landing' })}>Buscar profesores</Button>}
              />
            ) : (
              <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
                {favoriteTeachers.map((teacher) => (
                  <TeacherCard
                    key={teacher.id}
                    teacher={teacher}
                    onOpen={(id) => navigate({ name: 'profile', id })}
                    onToggleFavorite={toggleFavorite}
                    favorite
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </main>

      <Footer onNavigate={() => navigate({ name: 'landing' })} />

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
        teacher={contactTarget}
        onClose={() => setContactTarget(null)}
        onSent={(conversationId) => {
          setContactTarget(null);
          notify('Mensaje enviado');
          navigate({ name: 'messages', conversationId });
        }}
        onNeedsProfile={() => openProfileForm(contactTarget)}
      />
      <StudentProfileModal
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
        onSaved={handleProfileSaved}
        initial={profile}
      />
    </div>
  );
}
