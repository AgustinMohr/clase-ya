import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/Button';
import { Field, Input } from '../components/ui/Field';
import { Card } from '../components/ui/primitives';
import { ROUTES } from '../routes';

type State = 'loading' | 'done' | 'error';

interface Props {
  onLogin: () => void;
}

export default function ActivateAccountPage({ onLogin }: Props) {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';
  const { activate, resendActivation } = useAuth();
  const [state, setState] = useState<State>('loading');
  const [message, setMessage] = useState('');
  const [email, setEmail] = useState('');
  const [resendState, setResendState] = useState<'idle' | 'sending' | 'sent'>('idle');
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!token) {
      setState('error');
      setMessage('Falta el token de activación. Abrí el enlace completo que te llegó por email.');
      return;
    }
    activate(token)
      .then(() => setState('done'))
      .catch((error) => {
        setState('error');
        setMessage(
          error instanceof ApiError
            ? error.message
            : 'No pudimos activar la cuenta. Revisá tu conexión e intentá de nuevo.',
        );
      });
  }, [token, activate]);

  async function resend(ev: React.FormEvent) {
    ev.preventDefault();
    if (!email.trim()) return;
    setResendState('sending');
    try {
      await resendActivation(email.trim());
    } catch {
      // The endpoint answers 202 even for unknown emails; a failure here is transport-level.
    } finally {
      setResendState('sent');
    }
  }

  return (
    <div className="container-page max-w-xl py-10">
      <Card className="p-6 sm:p-8">
        {state === 'loading' && (
          <div role="status" aria-live="polite" className="space-y-3">
            <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">Activando tu cuenta</h1>
            <p className="text-content-muted">Un segundo, estamos validando el enlace.</p>
          </div>
        )}

        {state === 'done' && (
          <div role="status" aria-live="polite" className="space-y-3">
            <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">Cuenta activa</h1>
            <p className="text-content-muted">Ya podés ingresar con tu email y contraseña.</p>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <Button block onClick={onLogin}>
                Ingresar
              </Button>
              <Button variant="outline" block onClick={() => navigate(ROUTES.home)}>
                Ir al inicio
              </Button>
            </div>
          </div>
        )}

        {state === 'error' && (
          <div className="space-y-4">
            <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">No pudimos activar la cuenta</h1>
            <p role="alert" className="text-content-muted">
              {message}
            </p>
            <p className="text-sm text-content-muted">
              El enlace vence a las 24 horas y se puede usar una sola vez. Podés pedir uno nuevo:
            </p>
            {resendState === 'sent' ? (
              <p role="status" className="rounded-xl border border-border bg-surface-muted px-3 py-2 text-sm">
                Si el email corresponde a una cuenta pendiente, te enviamos un enlace nuevo.
              </p>
            ) : (
              <form onSubmit={resend} className="space-y-3" noValidate>
                <Field label="Email de la cuenta">
                  {(props) => (
                    <Input
                      {...props}
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                      required
                    />
                  )}
                </Field>
                <Button type="submit" block loading={resendState === 'sending'}>
                  Reenviar enlace
                </Button>
              </form>
            )}
            <Link to={ROUTES.home} className="inline-block text-sm font-semibold text-primary-700 underline dark:text-primary-200">
              Volver al inicio
            </Link>
          </div>
        )}
      </Card>
    </div>
  );
}
