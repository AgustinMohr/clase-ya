import { cn } from '../../lib/cn';

export function Section({
  title,
  subtitle,
  action,
  children,
  className,
  id,
}: {
  title?: string;
  subtitle?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  id?: string;
}) {
  return (
    <section id={id} className={cn('py-12 sm:py-16', className)}>
      <div className="container-page">
        {(title || action) && (
          <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
            <div>
              {title && <h2 className="font-display text-2xl sm:text-3xl">{title}</h2>}
              {subtitle && <p className="mt-1 max-w-2xl text-content-muted">{subtitle}</p>}
            </div>
            {action}
          </header>
        )}
        {children}
      </div>
    </section>
  );
}
