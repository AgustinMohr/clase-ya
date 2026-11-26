import { useEffect, useState } from 'react';
import { ApiError } from '../api';
import { useAuth } from '../auth/AuthContext';
import { Button } from './ui/Button';

export const RESEND_COOLDOWN_SECONDS = 30;

/**
 * Asks for a new activation link. The wait starts on mount, because the screen that shows this
 * button appears right after the first email was queued. It only spaces out clicks; the server
 * rate limit is the real control.
 */
export default function ResendActivationButton({ email }: { email: string }) {
  const { resendActivation } = useAuth();
  const [secondsLeft, setSecondsLeft] = useState(RESEND_COOLDOWN_SECONDS);
  const [sending, setSending] = useState(false);
  const [info, setInfo] = useState('');

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setTimeout(() => setSecondsLeft((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [secondsLeft]);

  async function resend() {
    setSending(true);
    setInfo('');
    try {
      await resendActivation(email);
      // The endpoint answers the same for any email, so the message must not confirm the account.
      setInfo('Si el email corresponde a una cuenta pendiente, te enviamos un enlace nuevo.');
    } catch (error) {
      setInfo(
        error instanceof ApiError && error.status === 429
          ? 'Pediste demasiados enlaces. Probá de nuevo más tarde.'
          : 'No pudimos reenviar el enlace. Intentá de nuevo en unos minutos.',
      );
    } finally {
      setSending(false);
      setSecondsLeft(RESEND_COOLDOWN_SECONDS);
    }
  }

  return (
    <div className="space-y-2">
      <Button
        type="button"
        variant="outline"
        block
        loading={sending}
        disabled={sending || secondsLeft > 0}
        onClick={resend}
      >
        {secondsLeft > 0 ? `Reenviar correo (${secondsLeft} s)` : 'Reenviar correo'}
      </Button>
      {info && (
        <p role="status" className="text-sm text-content-muted">
          {info}
        </p>
      )}
    </div>
  );
}
