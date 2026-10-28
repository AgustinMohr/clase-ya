import { BadgeCheck, Check, Circle } from 'lucide-react';
import type { AnnouncementStatus } from '../../../api';
import { Badge, Card } from '../../ui/primitives';

/** Announcement completeness (RF-2). Informative only: it never blocks editing. */
export default function AnnouncementStatusCard({
  announcement,
}: {
  announcement: AnnouncementStatus | null;
}) {
  const checklist = [
    { done: announcement?.hasName, label: 'Nombre' },
    { done: announcement?.hasBio, label: 'Bio' },
    { done: announcement?.hasSubject, label: 'Al menos una materia' },
    { done: announcement?.hasModality, label: 'Al menos una modalidad' },
    { done: announcement?.hasAvailability, label: 'Al menos un horario' },
  ];

  return (
    <Card className="border-primary-200 p-6 ring-1 ring-primary-100 dark:ring-primary-900/40">
      <h2 className="mb-4 font-display text-xl">Estado del anuncio</h2>
      <div className="mb-3">
        {announcement?.completeness === 'PUBLISHED' ? (
          <Badge tone="success" icon={<BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />}>Publicado</Badge>
        ) : (
          <Badge tone="neutral">Incompleto</Badge>
        )}
      </div>
      <ul className="space-y-1.5 text-sm">
        {checklist.map((item) => (
          <li key={item.label} className="flex items-center gap-2">
            {item.done ? (
              <Check className="h-4 w-4 shrink-0 text-success-600 dark:text-success-500" aria-hidden="true" />
            ) : (
              <Circle className="h-4 w-4 shrink-0 text-content-muted" aria-hidden="true" />
            )}
            <span className={item.done ? 'text-content' : 'text-content-muted'}>
              {item.label}
              <span className="sr-only">{item.done ? ' — completado' : ' — pendiente'}</span>
            </span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
