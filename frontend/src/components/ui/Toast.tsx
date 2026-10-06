import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { cn } from '../../lib/cn';

type ToastTone = 'success' | 'error' | 'info';

/** Optional action (e.g. "Deshacer") rendered inside the toast. */
interface ToastAction {
  label: string;
  onClick: () => void | Promise<void>;
}

interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
  action?: ToastAction;
}

interface ToastOptions {
  tone?: ToastTone;
  action?: ToastAction;
}

interface ToastContextValue {
  notify: (message: string, options?: ToastTone | ToastOptions) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

/**
 * Informative toasts dismiss on their own; action toasts stay longer so there is
 * time to read the message and hit the action (undo). Documented in DESIGN.md §8.11.
 */
const INFORMATIVE_DURATION = 4200;
const ACTION_DURATION = 8000;

const toneStyles: Record<ToastTone, string> = {
  success: 'border-success-500/30 bg-success-50 text-success-700 dark:bg-success-700/20 dark:text-success-500',
  error: 'border-error-500/30 bg-error-50 text-error-700 dark:bg-error-700/20 dark:text-error-500',
  info: 'border-border bg-surface text-content',
};

const toneIcon = {
  success: CheckCircle2,
  error: AlertTriangle,
  info: Info,
};

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, arg?: ToastTone | ToastOptions) => {
      const options: ToastOptions = typeof arg === 'string' ? { tone: arg } : arg ?? {};
      const id = Date.now() + Math.random();
      setToasts((current) => [...current, { id, tone: options.tone ?? 'info', message, action: options.action }]);
      window.setTimeout(() => dismiss(id), options.action ? ACTION_DURATION : INFORMATIVE_DURATION);
    },
    [dismiss],
  );

  const value = useMemo(() => ({ notify }), [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div aria-live="polite" aria-atomic="true" className="pointer-events-none fixed inset-x-0 bottom-4 z-[60] flex flex-col items-center gap-2 px-4">
        {toasts.map((toast) => {
          const Icon = toneIcon[toast.tone];
          return (
            <div
              key={toast.id}
              className={cn(
                'pointer-events-auto flex w-full max-w-sm items-start gap-2 rounded-xl border px-4 py-3 text-sm font-semibold shadow-card animate-fade-up',
                toneStyles[toast.tone],
              )}
            >
              <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span className="flex-1">{toast.message}</span>
              {toast.action && (
                <button
                  type="button"
                  onClick={() => {
                    void toast.action!.onClick();
                    dismiss(toast.id);
                  }}
                  className="-my-1 shrink-0 self-center rounded px-1.5 py-1 font-semibold underline underline-offset-2 hover:opacity-80"
                >
                  {toast.action.label}
                </button>
              )}
              <button
                type="button"
                aria-label="Cerrar aviso"
                onClick={() => dismiss(toast.id)}
                className="rounded p-0.5 opacity-60 transition-opacity hover:opacity-100"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
