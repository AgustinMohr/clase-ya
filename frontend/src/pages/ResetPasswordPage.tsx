import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api, ApiError } from '../api';
import { Button } from '../components/ui/Button';
import { Field, Input } from '../components/ui/Field';
import { Card } from '../components/ui/primitives';
import { validatePasswordReset } from '../lib/validation';
import { ROUTES } from '../routes';

interface Props {
  onLogin: () => void;
}

/** AUTH-003: consume the single-use reset token and choose a new password. */
export default function ResetPasswordPage({ onLogin }: Props) {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError('');
    const next = validatePasswordReset({ password, confirmation });
    setErrors(next);
    if (Object.keys(next).length > 0 || !token) return;

    setLoading(true);
    try {
      await api.resetPassword(token, password);
      setDone(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 400) {
        setError('El enlace de recuperación es inválido o ya venció. Pedí uno nuevo.');
      } else if (e instanceof ApiError && e.status === 429) {
        setError('Demasiados intentos. Esperá unos minutos e intentá de nuevo.');
      } else {
        setError('No pudimos restablecer la contraseña. Intentá de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  }

  if (!token) {
    return (
      <div className="container-page max-w-xl py-10">
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">
            Enlace inválido
          </h1>
          <p className="mt-3 text-content-muted" role="alert">
            Falta el token de recuperación. Abrí el enlace completo que te llegó por email.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row">
            <Link to={ROUTES.forgotPassword} className="font-semibold text-primary-700 underline dark:text-primary-200">
              Pedir un enlace nuevo
            </Link>
            <Link to={ROUTES.home} className="font-semibold text-primary-700 underline dark:text-primary-200">
              Volver al inicio
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  if (done) {
    return (
      <div className="container-page max-w-xl py-10">
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">
            Contraseña actualizada
          </h1>
          <p className="mt-3 text-content-muted" role="status">
            Ya podés ingresar con tu nueva contraseña.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row">
            <Button block onClick={onLogin}>
              Ingresar
            </Button>
            <Link
              to={ROUTES.home}
              className="inline-flex items-center justify-center rounded-xl border border-border px-5 py-3 text-sm font-semibold hover:bg-surface-muted"
            >
              Volver al inicio
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="container-page max-w-xl py-10">
      <Card className="p-6 sm:p-8">
        <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">
          Elegí una nueva contraseña
        </h1>
        <p className="mt-2 text-sm text-content-muted">
          El enlace vence en 30 minutos y se puede usar una sola vez.
        </p>

        <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
          <Field label="Nueva contraseña" hint="Al menos 10 caracteres, con letras y números." error={errors.password}>
            {(props) => (
              <Input
                {...props}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="new-password"
                required
              />
            )}
          </Field>

          <Field label="Repetir contraseña" error={errors.confirmation}>
            {(props) => (
              <Input
                {...props}
                type="password"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
                autoComplete="new-password"
                required
              />
            )}
          </Field>

          {error && (
            <p role="alert" className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
              {error}
            </p>
          )}

          <Button type="submit" block loading={loading}>
            Guardar contraseña
          </Button>
        </form>

        <p className="mt-5 text-sm text-content-muted">
          <Link to={ROUTES.forgotPassword} className="font-semibold text-primary-700 underline dark:text-primary-200">
            Pedir un enlace nuevo
          </Link>
        </p>
      </Card>
    </div>
  );
}
