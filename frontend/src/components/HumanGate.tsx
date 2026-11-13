import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, ShieldCheck } from 'lucide-react';
import { api } from '../api';
import TurnstileWidget from './TurnstileWidget';
import { Button } from './ui/Button';
import { Card } from './ui/primitives';

type GateState = 'checking' | 'required' | 'ready' | 'error';

interface Props {
  children: React.ReactNode;
}

export default function HumanGate({ children }: Props) {
  const [state, setState] = useState<GateState>('checking');
  const [token, setToken] = useState('');
  const [verifying, setVerifying] = useState(false);
  const verifyingRef = useRef(false);
  const [error, setError] = useState('');
  const [widgetKey, setWidgetKey] = useState(0);

  const check = useCallback(async () => {
    setState('checking');
    setError('');
    try {
      const status = await api.humanStatus();
      setState(status.required && !status.verified ? 'required' : 'ready');
    } catch {
      setState('error');
      setError('No pudimos verificar tu acceso. Revisá tu conexión e intentá de nuevo.');
    }
  }, []);

  useEffect(() => {
    void check();
  }, [check]);

  useEffect(() => {
    if (state !== 'required' || !token || verifyingRef.current) return;
    let active = true;
    verifyingRef.current = true;
    setVerifying(true);
    setError('');
    api
      .humanVerify(token)
      .then(() => {
        if (active) {
          setToken('');
          setState('ready');
        }
      })
      .catch(() => {
        if (active) {
          setError('No pudimos verificar que sos una persona. Intentá de nuevo.');
          setToken('');
          setWidgetKey((value) => value + 1);
        }
      })
      .finally(() => {
        verifyingRef.current = false;
        if (active) setVerifying(false);
      });
    return () => {
      active = false;
      verifyingRef.current = false;
    };
  }, [state, token]);

  if (state === 'ready') return <>{children}</>;

  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 py-10">
      <Card className="w-full max-w-md p-8 text-center">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-50 text-primary-700 dark:bg-primary-900/40 dark:text-primary-200">
          <ShieldCheck className="h-6 w-6" aria-hidden="true" />
        </div>
        <h1 className="font-display text-2xl">Antes de continuar</h1>
        <p className="mt-2 text-sm text-content-muted">
          Verificá que sos una persona para entrar a ClaseYa. Es una comprobación rápida y solo
          aparece la primera vez.
        </p>

        {state === 'checking' && (
          <div role="status" aria-busy="true" className="mt-6 flex items-center justify-center gap-2 text-sm text-content-muted">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Comprobando acceso…
          </div>
        )}

        {state === 'error' && (
          <>
            <p role="alert" className="mt-5 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
              {error}
            </p>
            <Button className="mt-4" onClick={() => void check()}>
              Reintentar
            </Button>
          </>
        )}

        {state === 'required' && (
          <>
            <div className="mt-6 flex min-h-[65px] justify-center">
              <TurnstileWidget
                key={widgetKey}
                onToken={setToken}
                onError={(message) => {
                  setError(message);
                  setWidgetKey((value) => value + 1);
                }}
              />
            </div>
            {verifying && (
              <p role="status" className="mt-2 flex items-center justify-center gap-2 text-sm text-content-muted">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Verificando…
              </p>
            )}
            {!verifying && !token && !error && (
              <p className="mt-2 text-xs text-content-muted">Completá la verificación para entrar.</p>
            )}
            {error && (
              <p role="alert" className="mt-3 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
                {error}
              </p>
            )}
          </>
        )}
      </Card>
    </div>
  );
}
