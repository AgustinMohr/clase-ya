import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { ApiError } from '../api';
import { ROUTES } from '../routes';
import GoogleSignInButton from './GoogleSignInButton';
import Modal from './ui/Modal';
import { Button } from './ui/Button';
import { Field, Input } from './ui/Field';

interface Props {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function LoginModal({ open, onClose, onSuccess }: Props) {
  const { login, loginWithGoogle, resendActivation } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pendingGoogle, setPendingGoogle] = useState<string | null>(null);
  const [needsRole, setNeedsRole] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [pendingHelp, setPendingHelp] = useState(false);
  const [resendInfo, setResendInfo] = useState('');
  const [resending, setResending] = useState(false);

  function describe(e: unknown): string {
    if (e instanceof ApiError) {
      const detail = e.message && e.message.trim().length > 0 ? e.message : 'Error';
      return `[${e.status}] ${detail}`;
    }
    return 'No pudimos conectar con el servidor.';
  }

  async function submitPassword(ev: React.FormEvent) {
    ev.preventDefault();
    setError('');
    setPendingHelp(false);
    setResendInfo('');
    setLoading(true);
    try {
      await login(email, password);
      onSuccess();
    } catch (e) {
      setError(describe(e));
      // A pending account gets the same generic 401 as a wrong password (anti-enumeration), so
      // the hint is offered to everyone instead of trying to detect the account state.
      if (e instanceof ApiError && e.status === 401) setPendingHelp(true);
    } finally {
      setLoading(false);
    }
  }

  async function resend() {
    if (!email.trim()) {
      setResendInfo('Escribí el email de la cuenta para reenviar el enlace.');
      return;
    }
    setResending(true);
    try {
      await resendActivation(email.trim());
      setResendInfo('Si el email corresponde a una cuenta pendiente, te enviamos un enlace nuevo.');
    } catch {
      setResendInfo('No pudimos reenviar el enlace. Intentá de nuevo en unos minutos.');
    } finally {
      setResending(false);
    }
  }

  function goToRegister() {
    onClose();
    navigate(ROUTES.register);
  }

  async function googleWith(idToken: string, role?: 'STUDENT' | 'TEACHER') {
    setError('');
    setLoading(true);
    try {
      await loginWithGoogle(idToken, role);
      setPendingGoogle(null);
      setNeedsRole(false);
      onSuccess();
    } catch (e) {
      if (e instanceof ApiError && e.status === 400 && /role/i.test(e.message)) {
        setPendingGoogle(idToken);
        setNeedsRole(true);
      } else {
        setError(describe(e));
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Ingresar" size="sm">
      {!needsRole ? (
        <div className="space-y-5">
          <form onSubmit={submitPassword} className="space-y-4" noValidate>
            <Field label="Email">
              {(props) => (
                <Input
                  {...props}
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                />
              )}
            </Field>
            <Field label="Contraseña">
              {(props) => (
                <Input
                  {...props}
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                />
              )}
            </Field>
            <Button type="submit" block loading={loading}>
              Ingresar
            </Button>
          </form>

          <div className="flex items-center gap-3 text-xs uppercase tracking-wide text-content-muted">
            <span className="h-px flex-1 bg-border" /> o <span className="h-px flex-1 bg-border" />
          </div>

          <GoogleSignInButton
            onCredential={(idToken) => googleWith(idToken)}
            onError={(message) => setError(message)}
            disabled={loading}
          />

          <p className="text-sm text-content-muted">
            ¿No tenés cuenta?{' '}
            <button
              type="button"
              onClick={goToRegister}
              className="font-semibold text-primary-700 underline dark:text-primary-200"
            >
              Crear cuenta
            </button>
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          <p className="text-sm text-content-muted">¿Cómo vas a usar ClaseYa?</p>
          <Button block loading={loading} onClick={() => googleWith(pendingGoogle!, 'STUDENT')}>
            Busco clases (estudiante)
          </Button>
          <Button block variant="secondary" loading={loading} onClick={() => googleWith(pendingGoogle!, 'TEACHER')}>
            Doy clases (profesor)
          </Button>
        </div>
      )}

      {error && (
        <p role="alert" className="mt-4 rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
          {error}
        </p>
      )}

      {pendingHelp && (
        <div className="mt-3 rounded-xl border border-border bg-surface-muted px-3 py-2 text-sm">
          <p className="text-content-muted">
            Si te registraste hace poco, la cuenta puede estar esperando activación: revisá el email
            (también spam) o pedí un enlace nuevo.
          </p>
          <button
            type="button"
            onClick={resend}
            disabled={resending}
            className="mt-1 font-semibold text-primary-700 underline disabled:opacity-60 dark:text-primary-200"
          >
            {resending ? 'Reenviando…' : 'Reenviar enlace de activación'}
          </button>
          {resendInfo && (
            <p role="status" className="mt-1 text-xs text-content-muted">
              {resendInfo}
            </p>
          )}
        </div>
      )}
    </Modal>
  );
}
