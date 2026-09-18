import { Heart, MapPin, Star, BadgeCheck } from 'lucide-react';
import type { TeacherSummary } from '../../api';
import { Avatar, Badge, Card } from '../ui/primitives';
import { Button } from '../ui/Button';
import { cn } from '../../lib/cn';
import { formatPriceHour, modalityLabel } from '../../lib/format';

interface Props {
  teacher: TeacherSummary;
  onOpen: (id: string) => void;
  onToggleFavorite?: (teacher: TeacherSummary) => void;
  favorite?: boolean;
  featured?: boolean;
}

export function TeacherCard({ teacher, onOpen, onToggleFavorite, favorite, featured }: Props) {
  const subjects = (teacher.subjects ?? []).map((s) => s.subjectName).filter(Boolean).slice(0, 3);
  const modalities = (teacher.modalities ?? []).map(modalityLabel);

  return (
    <Card
      interactive
      className={cn('relative flex h-full flex-col gap-3 p-5', featured && 'border-primary-200 ring-1 ring-primary-100 dark:ring-primary-900/40')}
      onClick={() => onOpen(teacher.id)}
    >
      <div className="flex items-start gap-3">
        <Avatar src={teacher.photoUrl} name={teacher.displayName} size="lg" />
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <h3 className="truncate text-lg font-bold leading-tight">{teacher.displayName || 'Profesor'}</h3>
            {onToggleFavorite && (
              <button
                type="button"
                aria-label={favorite ? `Quitar a ${teacher.displayName} de favoritos` : `Guardar a ${teacher.displayName} en favoritos`}
                aria-pressed={favorite}
                onClick={(e) => {
                  e.stopPropagation();
                  onToggleFavorite(teacher);
                }}
                className={cn(
                  'rounded-full p-1.5 transition-colors',
                  favorite ? 'text-error-600' : 'text-content-muted hover:text-error-600',
                )}
              >
                <Heart className={cn('h-5 w-5', favorite && 'fill-current')} aria-hidden="true" />
              </button>
            )}
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            {teacher.verificationStatus === 'VERIFIED' && (
              <Badge tone="success" icon={<BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />}>
                Verificado
              </Badge>
            )}
            <span className="inline-flex items-center gap-1 text-sm text-content-muted">
              <Star className={cn('h-4 w-4', teacher.ratingCount ? 'fill-accent-400 text-accent-400' : 'text-content-muted/50')} aria-hidden="true" />
              {teacher.ratingCount ? `${Number(teacher.ratingAverage).toFixed(1)} (${teacher.ratingCount})` : 'Sin opiniones'}
            </span>
          </div>
        </div>
      </div>

      {teacher.bio && <p className="line-clamp-2 text-sm text-content-muted">{teacher.bio}</p>}

      {subjects.length > 0 && (
        <p className="text-sm">
          <span className="font-semibold">Materias:</span> {subjects.join(' · ')}
        </p>
      )}

      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
        <div className="flex flex-wrap items-center gap-2 text-sm text-content-muted">
          {teacher.city && (
            <span className="inline-flex items-center gap-1">
              <MapPin className="h-4 w-4" aria-hidden="true" /> {teacher.city}
            </span>
          )}
          {modalities.map((m) => (
            <Badge key={m} tone="primary">
              {m}
            </Badge>
          ))}
        </div>
        <div className="text-right">
          <p className="font-display text-lg text-primary-700 dark:text-primary-200">{formatPriceHour(teacher.pricePerHour)}</p>
        </div>
      </div>

      <Button
        size="sm"
        block
        onClick={(e) => {
          e.stopPropagation();
          onOpen(teacher.id);
        }}
      >
        Ver perfil
      </Button>
    </Card>
  );
}
