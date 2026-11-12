import { useState } from 'react';
import { Link } from 'react-router';
import { api, ApiError } from '../api';
import { Button } from '../components/ui/Button';
import { Field, Input } from '../components/ui/Field';
import { Card } from '../components/ui/primitives';
import { ROUTES } from '../routes';

/** AUTH-003: request a single-use password-reset link. Always answers with the same message. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!email.trim()) {
      setError('Ingresá tu email.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.forgotPassword(email.trim());
      setSent(true);
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 503
          ? 'No pudimos enviar el email. Intentá de nuevo en unos minutos.'
          : 'No pudimos procesar la solicitud. Intentá de nuevo.',
      );
    } finally {
      setLoading(false);
    }
  }

  if (sent) {
    return (
      <div className="container-page max-w-xl py-10">
        <Card className="p-6 sm:p-8">
          <h1 className="font-display text-3xl text-primary-700 dark:text-primary-200">
            Revisá tu email
          </h1>
          <p className="mt-3 text-content-muted" role="status">
            Si el email corresponde a una cuenta activa, te enviamos un enlace para restablecer la
            contraseña. El enlace vence en 30 minutos y se puede usar una sola vez.
          </p>
          <div className="mt-6">
            <Link
              to={ROUTES.home}
              className="inline-flex text-sm font-semibold text-primary-700 underline dark:text-primary-200"
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
          Recuperar contraseña
        </h1>
        <p className="mt-2 text-sm text-content-muted">
          Ingresá el email de tu cuenta y te enviamos un enlace de un solo uso.
        </p>

        <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
          <Field label="Email">
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

          {error && (
            <p role="alert" className="rounded-xl border border-error-500/30 bg-error-50 px-3 py-2 text-sm font-semibold text-error-700 dark:bg-error-700/20 dark:text-error-500">
              {error}
            </p>
          )}

          <Button type="submit" block loading={loading}>
            Enviar enlace
          </Button>
        </form>

        <p className="mt-5 text-sm text-content-muted">
          <Link to={ROUTES.home} className="font-semibold text-primary-700 underline dark:text-primary-200">
            Volver al inicio
          </Link>
        </p>
      </Card>
    </div>
  );
}
