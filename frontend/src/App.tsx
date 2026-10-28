import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { api, ApiError, type StudentProfile, type Subject, type TeacherSummary } from './api';
import { useAuth } from './auth/AuthContext';
import { useToast } from './components/ui/Toast';
import { Navbar } from './components/ui/Navbar';
import { Footer } from './components/ui/Footer';
import { Skeleton } from './components/ui/primitives';
import { ProtectedRoute } from './components/routing/ProtectedRoute';
import LoginModal from './components/LoginModal';
import ContactModal from './components/ContactModal';
import StudentProfileModal from './components/StudentProfileModal';
import { ROUTES, isPrivatePath, searchPath } from './routes';

const LandingPage = lazy(() => import('./pages/LandingPage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const TeacherProfilePage = lazy(() => import('./pages/TeacherProfilePage'));
const MessagesPage = lazy(() => import('./pages/MessagesPage'));
const MyListingPage = lazy(() => import('./pages/MyListingPage'));
const FavoritesPage = lazy(() => import('./pages/FavoritesPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const ActivateAccountPage = lazy(() => import('./pages/ActivateAccountPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));

function RouteFallback() {
  return (
    <div className="container-page space-y-5 py-8" aria-busy="true" aria-label="Cargando página">
      <Skeleton className="h-40" />
      <Skeleton className="h-64" />
    </div>
  );
}

function TeacherProfileRoute({
  favorites,
  onContact,
  onToggleFavorite,
}: {
  favorites: Set<string>;
  onContact: (teacher: TeacherSummary) => void;
  onToggleFavorite: (teacher: TeacherSummary) => void;
}) {
  const { teacherId = '' } = useParams();
  const { user } = useAuth();
  return (
    <TeacherProfilePage
      teacherId={teacherId}
      onContact={onContact}
      onToggleFavorite={user && user.role !== 'STUDENT' ? undefined : onToggleFavorite}
      favorite={favorites.has(teacherId)}
    />
  );
}

export default function App() {
  const { user } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [favorites, setFavorites] = useState<Set<string>>(new Set());
  const [favoriteTeachers, setFavoriteTeachers] = useState<TeacherSummary[]>([]);
  const [favoritesTotal, setFavoritesTotal] = useState(0);
  const [favoritesLoading, setFavoritesLoading] = useState(Boolean(user));
  const [loginOpen, setLoginOpen] = useState(false);
  const [pendingContact, setPendingContact] = useState<TeacherSummary | null>(null);
  const [contactTarget, setContactTarget] = useState<TeacherSummary | null>(null);
  // Student profile: null means it does not exist yet, which blocks both contacting
  // a teacher and saving favorites (the API answers 409). CONTACT-001 RF-1.
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [profileChecked, setProfileChecked] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const requireLogin = useCallback(() => setLoginOpen(true), []);

  useEffect(() => {
    api.subjects().then(setSubjects).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!user) {
      setFavorites(new Set());
      setFavoriteTeachers([]);
      setFavoritesTotal(0);
      setFavoritesLoading(false);
      return;
    }
    let active = true;
    setFavoritesLoading(true);
    api
      .favorites()
      .then((page) => {
        if (!active) return;
        setFavoriteTeachers(page.content.map((f) => f.teacher));
        setFavorites(new Set(page.content.map((f) => f.teacher.id)));
        setFavoritesTotal(page.totalElements);
      })
      .catch(() => undefined)
      .finally(() => active && setFavoritesLoading(false));
    return () => {
      active = false;
    };
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
    let active = true;
    api
      .studentProfile()
      .then((saved) => active && setProfile(saved))
      .catch(() => active && setProfile(null))
      .finally(() => active && setProfileChecked(true));
    return () => {
      active = false;
    };
  }, [user]);

  // Logging out (or an expired session) must not keep showing a private view. The ref keeps
  // an anonymous deep link on its URL so the guard can offer the login instead of redirecting.
  const wasAuthenticated = useRef(Boolean(user));
  useEffect(() => {
    if (wasAuthenticated.current && !user && isPrivatePath(location.pathname)) {
      navigate(ROUTES.home, { replace: true });
    }
    wasAuthenticated.current = Boolean(user);
  }, [user, location.pathname, navigate]);

  // A navigation starts at the top. Query-only changes (filters, pagination) keep the position.
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0 });
  }, [location.pathname]);

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
        setFavoritesTotal((prev) => Math.max(0, prev - 1));
        notify('Quitado de favoritos');
      } else {
        await api.addFavorite(teacher.id);
        setFavorites((prev) => new Set(prev).add(teacher.id));
        setFavoriteTeachers((prev) => [teacher, ...prev.filter((t) => t.id !== teacher.id)]);
        setFavoritesTotal((prev) => prev + 1);
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

  return (
    <div className="flex min-h-screen flex-col">
      <Navbar onProfile={() => setProfileOpen(true)} onLogin={() => setLoginOpen(true)} />

      <main className="flex-1">
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route
              path={ROUTES.home}
              element={
                <LandingPage
                  onSearch={(term) => navigate(searchPath(term))}
                  onOpenTeacher={(id) => navigate(ROUTES.teacher(id))}
                  onToggleFavorite={toggleFavorite}
                  favorites={favorites}
                />
              }
            />

            <Route
              path={ROUTES.search}
              element={
                <SearchPage
                  subjects={subjects}
                  onBack={() => navigate(ROUTES.home)}
                  onOpenTeacher={(id) => navigate(ROUTES.teacher(id))}
                  onToggleFavorite={toggleFavorite}
                  favorites={favorites}
                />
              }
            />

            <Route
              path="/profesores/:teacherId"
              element={
                <TeacherProfileRoute
                  favorites={favorites}
                  onContact={contact}
                  onToggleFavorite={toggleFavorite}
                />
              }
            />

            <Route
              path={ROUTES.favorites}
              element={
                <ProtectedRoute role="STUDENT" onRequireLogin={requireLogin}>
                  <FavoritesPage
                    teachers={favoriteTeachers}
                    total={favoritesTotal}
                    loading={favoritesLoading}
                    onToggleFavorite={toggleFavorite}
                  />
                </ProtectedRoute>
              }
            />

            <Route
              path={ROUTES.messages}
              element={
                <ProtectedRoute onRequireLogin={requireLogin}>
                  <MessagesPage />
                </ProtectedRoute>
              }
            />

            <Route
              path={`${ROUTES.messages}/:conversationId`}
              element={
                <ProtectedRoute onRequireLogin={requireLogin}>
                  <MessagesPage />
                </ProtectedRoute>
              }
            />

            <Route
              path={ROUTES.myListing}
              element={
                <ProtectedRoute role="TEACHER" onRequireLogin={requireLogin}>
                  <MyListingPage
                    onBack={() => navigate(ROUTES.home)}
                    onPreview={(id) => navigate(ROUTES.teacher(id))}
                  />
                </ProtectedRoute>
              }
            />

            <Route path={ROUTES.register} element={<RegisterPage onLogin={requireLogin} />} />

            <Route path={ROUTES.activate} element={<ActivateAccountPage onLogin={requireLogin} />} />

            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </main>

      <Footer onNavigate={() => navigate(ROUTES.home)} />

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
          navigate(ROUTES.conversation(conversationId));
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
