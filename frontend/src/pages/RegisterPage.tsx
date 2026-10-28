import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/ui/Button';
import { Field, Input } from '../components/ui/Field';
import { Card } from '../components/ui/primitives';
import { ROUTES } from '../routes';

type AccountRole = 'STUDENT' | 'TEACHER';

interface Props {
  onLogin: () => void;
}

function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  const body = error.body as { fieldErrors?: Record<string, string> } | undefined;
  return body?.fieldErrors ?? {};
}

export default function RegisterPage({ onLogin }: Props) {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [role, setRole] = useState<AccountRole>('STUDENT');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);

  function validate(): boolean {
    const next: Record<string, string> = {};
    if (!email.trim()) next.email = 'Ingresá tu email.';
    if (password.length < 10 || !/\p{L}/u.test(password) || !/\d/.test(password)) {
      next.password = 'Usá al menos 10 caracteres, con letras y números.';
    }
    if (confirmation !== password) next.confirmation = 'Las contraseñas no coinciden.';
    setErrors(next);
    return Object.keys(next).length === 0;
  }

  async function submit(ev: React.FormEvent) {
    ev.preventDefault();
    setFormError('');
    if (!validate()) return;
    setLoading(true);
    try {
      await register(email.trim(), password, role);
      setSentTo(email.trim());
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setErrors(fieldErrorsOf(error));
        if (Object.keys(fieldErrorsOf(error)).length === 0) setFormError(error.message);
      } else if (error instanceof ApiError && error.status === 503) {
        setFormError('No pudimos enviar el email de activación. Intentá de nuevo en unos minutos.');
      } else {
        setFormError('No pudimos crear la cuenta. Intentá de nuevo.');
      }
    } finally {
      setLoading(false);
    }
  }

  if (sentTo) {
    return (
      <div className="container-page max-w-xl py-10">
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">Revisá tu email</h1>
          <p className="mt-3 text-content-muted" role="status">
            Si el email no tenía una cuenta, te enviamos un enlace de activación a{' '}
            <strong className="text-content">{sentTo}</strong>. Si ya existía, ingresá con tu
            contraseña. El enlace vence en 24 horas y se puede usar una sola vez.
          </p>
          <p className="mt-2 text-sm text-content-muted">
            Si no llega en unos minutos, revisá spam o pedí un enlace nuevo desde el ingreso.
          </p>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row">
            <Button block onClick={onLogin}>
              Ya activé mi cuenta
            </Button>
            <Button variant="outline" block onClick={() => navigate(ROUTES.home)}>
              Volver al inicio
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="container-page max-w-xl py-10">
      <Card className="p-6 sm:p-8">
        <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">Crear cuenta</h1>
        <p className="mt-2 text-sm text-content-muted">
          Elegí cómo vas a usar ClaseYa. Después te enviamos un email para activar la cuenta.
        </p>

        <form onSubmit={submit} className="mt-6 space-y-5" noValidate>
          <div role="group" aria-label="Tipo de cuenta" className="flex flex-wrap gap-2">
            <button
              type="button"
              aria-pressed={role === 'STUDENT'}
              onClick={() => setRole('STUDENT')}
              className={`rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
                role === 'STUDENT'
                  ? 'border-primary-600 bg-primary-600 text-white'
                  : 'border-border bg-surface text-content hover:border-primary-300'
              }`}
            >
              Busco clases
            </button>
            <button
              type="button"
              aria-pressed={role === 'TEACHER'}
              onClick={() => setRole('TEACHER')}
              className={`rounded-full border px-4 py-2 text-sm font-semibold transition-colors ${
                role === 'TEACHER'
                  ? 'border-primary-600 bg-primary-600 text-white'
                  : 'border-border bg-surface text-content hover:border-primary-300'
              }`}
            >
              Doy clases
            </button>
          </div>

          <Field label="Email" error={errors.email}>
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

          <Field
            label="Contraseña"
            hint="Al menos 10 caracteres, con letras y números."
            error={errors.password}
          >
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

          {formError && (
            <p role="alert" className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
              {formError}
            </p>
          )}

          <Button type="submit" block loading={loading}>
            Crear cuenta
          </Button>
        </form>

        <p className="mt-5 text-sm text-content-muted">
          ¿Ya tenés cuenta?{' '}
          <button type="button" onClick={onLogin} className="font-semibold text-primary-700 underline dark:text-primary-200">
            Ingresar
          </button>{' '}
          o{' '}
          <Link to={ROUTES.home} className="font-semibold text-primary-700 underline dark:text-primary-200">
            volver al inicio
          </Link>
          .
        </p>
      </Card>
    </div>
  );
}
