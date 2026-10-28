import { Heart } from 'lucide-react';
import { useNavigate } from 'react-router';
import type { TeacherSummary } from '../api';
import { usePageMeta } from '../hooks/usePageMeta';
import { Button } from '../components/ui/Button';
import { EmptyState, Skeleton } from '../components/ui/primitives';
import { TeacherCard } from '../components/teacher/TeacherCard';
import { ROUTES } from '../routes';

interface Props {
  teachers: TeacherSummary[];
  loading: boolean;
  onToggleFavorite: (teacher: TeacherSummary) => void;
}

/** "Mis favoritos": the teachers the student saved. Only mounted for authenticated students. */
export default function FavoritesPage({ teachers, loading, onToggleFavorite }: Props) {
  const navigate = useNavigate();
  usePageMeta({
    title: 'Mis favoritos | ClaseYa',
    description: 'Los profesores que guardaste en ClaseYa, listos para contactar.',
  });

  return (
    <div className="container-page py-8">
      <h1 className="mb-6 font-display text-3xl">Mis favoritos</h1>
      {loading ? (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-64" />
          ))}
        </div>
      ) : teachers.length === 0 ? (
        <EmptyState
          icon={<Heart className="h-6 w-6" aria-hidden="true" />}
          title="Todavía no guardaste profesores"
          description="Explorá el buscador y guardá los perfiles que te interesen."
          action={
            <Button onClick={() => navigate(ROUTES.home)}>Buscar profesores</Button>
          }
        />
      ) : (
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {teachers.map((teacher) => (
            <TeacherCard
              key={teacher.id}
              teacher={teacher}
              onOpen={(id) => navigate(ROUTES.teacher(id))}
              onToggleFavorite={onToggleFavorite}
              favorite
            />
          ))}
        </div>
      )}
    </div>
  );
}
