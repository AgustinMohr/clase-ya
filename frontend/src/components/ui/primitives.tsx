import { cva, type VariantProps } from 'class-variance-authority';
import { Star, BadgeCheck } from 'lucide-react';
import { cn } from '../../lib/cn';
import { formatRating, initials } from '../../lib/format';

/* ------------------------------------------------ Card */
export function Card({
  className,
  interactive,
  ...rest
}: React.HTMLAttributes<HTMLDivElement> & { interactive?: boolean }) {
  return (
    <div
      className={cn(
        'rounded-2xl border border-border bg-surface shadow-card',
        interactive &&
          'cursor-pointer transition-[transform,box-shadow] duration-200 ease-smooth hover:-translate-y-0.5 hover:shadow-card-hover',
        className,
      )}
      {...rest}
    />
  );
}

/* ------------------------------------------------ Badge */
const badgeStyles = cva(
  'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold',
  {
    variants: {
      tone: {
        neutral: 'bg-surface-muted text-content-muted',
        primary: 'bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200',
        success: 'bg-success-50 text-success-700 dark:bg-success-700/20 dark:text-success-500',
        accent: 'bg-accent-100 text-accent-700 dark:bg-accent-700/25 dark:text-accent-300',
        secondary: 'bg-secondary-100 text-secondary-700 dark:bg-secondary-700/25 dark:text-secondary-200',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

export function Badge({
  className,
  tone,
  icon,
  children,
}: React.HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeStyles> & { icon?: React.ReactNode }) {
  return (
    <span className={cn(badgeStyles({ tone }), className)}>
      {icon}
      {children}
    </span>
  );
}

export function VerifiedBadge({ className }: { className?: string }) {
  return (
    <Badge tone="success" className={className} icon={<BadgeCheck className="h-3.5 w-3.5" aria-hidden="true" />}>
      Verificado
    </Badge>
  );
}

/* ------------------------------------------------ Chip (filtros / atajos) */
export function Chip({
  className,
  active,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { active?: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      className={cn(
        'rounded-full border px-3.5 py-1.5 text-sm font-semibold transition-colors duration-200',
        active
          ? 'border-primary-600 bg-primary-600 text-white'
          : 'border-border bg-surface text-content hover:border-primary-300 hover:bg-surface-muted',
        className,
      )}
      {...rest}
    />
  );
}

/* ------------------------------------------------ Avatar */
const avatarSizes = { sm: 'h-9 w-9 text-sm', md: 'h-12 w-12 text-base', lg: 'h-16 w-16 text-xl', xl: 'h-24 w-24 text-3xl' };

export function Avatar({
  src,
  name,
  size = 'md',
  className,
}: {
  src?: string | null;
  name?: string | null;
  size?: keyof typeof avatarSizes;
  className?: string;
}) {
  if (src) {
    return (
      <img
        src={src}
        alt={name ? `Foto de ${name}` : 'Foto de perfil'}
        loading="lazy"
        className={cn('rounded-full object-cover', avatarSizes[size], className)}
      />
    );
  }
  return (
    <div
      aria-hidden="true"
      className={cn(
        'grid place-items-center rounded-full bg-primary-100 font-display text-primary-700 dark:bg-primary-900/50 dark:text-primary-200',
        avatarSizes[size],
        className,
      )}
    >
      {initials(name)}
    </div>
  );
}

/* ------------------------------------------------ Rating */
export function Rating({
  average,
  count,
  className,
}: {
  average?: number | null;
  count?: number | null;
  className?: string;
}) {
  const hasRating = Boolean(average && count);
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-sm', className)}>
      <Star
        className={cn('h-4 w-4', hasRating ? 'fill-accent-400 text-accent-400' : 'text-content-muted/50')}
        aria-hidden="true"
      />
      <span className={cn('font-semibold', hasRating ? 'text-content' : 'text-content-muted')}>
        {hasRating ? formatRating(average, count) : 'Sin opiniones'}
      </span>
    </span>
  );
}

/* ------------------------------------------------ Skeleton */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('skeleton rounded-xl', className)} aria-hidden="true" />;
}

/* ------------------------------------------------ EmptyState */
export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border bg-surface px-6 py-12 text-center',
        className,
      )}
    >
      {icon && <div className="grid h-12 w-12 place-items-center rounded-full bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">{icon}</div>}
      <h3 className="text-lg font-bold">{title}</h3>
      {description && <p className="max-w-md text-sm text-content-muted">{description}</p>}
      {action}
    </div>
  );
}
