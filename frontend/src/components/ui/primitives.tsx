import { useState } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { Star, BadgeCheck, X } from 'lucide-react';
import { cn } from '../../lib/cn';
import { formatRating, initials } from '../../lib/format';
import Modal from './Modal';
import { Button } from './Button';

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

/* ------------------------------------------------ SubCard (inset / sub-panel) */
/** Nested surface inside a Card. `dense` is the compact list-row form. */
export function SubCard({
  className,
  dense,
  ...rest
}: React.HTMLAttributes<HTMLDivElement> & { dense?: boolean }) {
  return (
    <div
      className={cn(
        'border border-border bg-surface-muted',
        dense ? 'rounded-lg px-3 py-2' : 'rounded-xl p-4',
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

/* ------------------------------------------------ Tag (valor editable) */
/**
 * Same visual language as Badge, but for values the user can remove (matters:
 * subjects, education). Badge stays presentational; Chip is a toggle button, so a
 * remove control cannot live inside it — a <span>-based Tag can.
 */
export function Tag({
  className,
  tone,
  icon,
  onRemove,
  removeLabel,
  children,
  ...rest
}: React.HTMLAttributes<HTMLSpanElement> &
  VariantProps<typeof badgeStyles> & {
    icon?: React.ReactNode;
    onRemove?: () => void;
    removeLabel?: string;
  }) {
  return (
    <span className={cn(badgeStyles({ tone }), 'gap-1.5', className)} {...rest}>
      {icon}
      {children}
      {onRemove && (
        <button
          type="button"
          aria-label={removeLabel ?? 'Quitar'}
          onClick={onRemove}
          className="-mr-0.5 rounded-full p-0.5 transition-colors hover:text-error-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      )}
    </span>
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
  size = 'default',
}: {
  icon?: React.ReactNode;
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
  size?: 'default' | 'compact';
}) {
  const compact = size === 'compact';
  return (
    <div
      className={cn(
        'flex flex-col items-center border border-dashed border-border bg-surface text-center',
        compact ? 'gap-2 rounded-xl px-4 py-6' : 'gap-3 rounded-2xl px-6 py-12',
        className,
      )}
    >
      {icon &&
        (compact ? (
          <div className="text-content-muted">{icon}</div>
        ) : (
          <div className="grid h-12 w-12 place-items-center rounded-full bg-primary-50 text-primary-600 dark:bg-primary-900/40 dark:text-primary-200">
            {icon}
          </div>
        ))}
      <h3 className={cn(compact ? 'text-sm font-semibold' : 'text-lg font-bold')}>{title}</h3>
      {description && <p className={cn('max-w-md text-content-muted', compact ? 'text-xs' : 'text-sm')}>{description}</p>}
      {action}
    </div>
  );
}

/* ------------------------------------------------ ConfirmDialog (acciones destructivas) */
/** Modal de confirmación para acciones irreversibles; el botón de confirmar usa `danger`. */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm: () => void | Promise<void>;
  onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);

  async function confirm() {
    setBusy(true);
    try {
      await onConfirm();
      onClose();
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={title} size="sm">
      {description && <p className="text-sm text-content-muted">{description}</p>}
      <div className="mt-5 flex flex-col gap-2 sm:flex-row">
        <Button variant="danger" block loading={busy} onClick={confirm}>
          {confirmLabel}
        </Button>
        <Button variant="outline" block onClick={onClose} disabled={busy}>
          {cancelLabel}
        </Button>
      </div>
    </Modal>
  );
}
