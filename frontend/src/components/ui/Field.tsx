import { useId } from 'react';
import { cn } from '../../lib/cn';

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  children: (props: { id: string; 'aria-invalid'?: boolean; 'aria-describedby'?: string }) => React.ReactNode;
}

/** Accessible label + control wiring (single source for inputs/selects/textareas). */
export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId();
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-content">
        {label}
      </label>
      {children({ id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && !error && (
        <p id={`${id}-hint`} className="text-xs text-content-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-xs font-semibold text-error-600">
          {error}
        </p>
      )}
    </div>
  );
}

const control =
  'w-full rounded-xl border border-border bg-surface px-3.5 py-2.5 text-[15px] text-content placeholder:text-content-muted/70 transition-colors duration-200 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-ring/40 disabled:opacity-50';

export function Input({ className, ...rest }: React.InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(control, className)} {...rest} />;
}

export function Textarea({ className, ...rest }: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className={cn(control, 'resize-y', className)} {...rest} />;
}

export function Select({ className, children, ...rest }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={cn(control, 'pr-8', className)} {...rest}>
      {children}
    </select>
  );
}
