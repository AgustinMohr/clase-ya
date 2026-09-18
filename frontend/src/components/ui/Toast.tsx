import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { CheckCircle2, AlertTriangle, Info, X } from 'lucide-react';
import { cn } from '../../lib/cn';

type ToastTone = 'success' | 'error' | 'info';
interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastContextValue {
  notify: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

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

  const notify = useCallback((message: string, tone: ToastTone = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, tone, message }]);
    window.setTimeout(() => setToasts((current) => current.filter((t) => t.id !== id)), 4200);
  }, []);

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
              <button
                type="button"
                aria-label="Cerrar aviso"
                onClick={() => setToasts((current) => current.filter((t) => t.id !== toast.id))}
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
