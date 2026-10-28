import { GoogleLogin } from '@react-oauth/google';
import { GOOGLE_CLIENT_ID } from '../auth/AuthContext';

interface Props {
  onCredential: (idToken: string) => void;
  onError?: (message: string) => void;
  disabled?: boolean;
}

/**
 * Renders Google Sign-In only when a client id is configured AND the
 * GoogleOAuthClientProvider is mounted (see main.tsx).
 *
 * `origin_mismatch` is a Google Cloud Console setting, not an application bug: the browser origin
 * must be listed under "Authorized JavaScript origins" of the OAuth client. The messages below
 * name the exact origin so the fix is obvious instead of a dead end.
 */
export default function GoogleSignInButton({ onCredential, onError, disabled }: Props) {
  if (!GOOGLE_CLIENT_ID) {
    return (
      <p className="hint">
        Login con Google no configurado: definí <code>VITE_GOOGLE_CLIENT_ID</code> para habilitarlo.
      </p>
    );
  }

  const origin = window.location.origin;
  const loopbackAlias = window.location.hostname === '127.0.0.1';
  const originHelp =
    `Si Google responde "origin_mismatch", agregá ${origin} en Google Cloud Console → ` +
    'APIs & Services → Credentials → tu OAuth client (Web application) → Authorized JavaScript origins.';

  return (
    <div className="space-y-2">
      <div className="google-btn" aria-disabled={disabled}>
        <GoogleLogin
          onSuccess={(credentialResponse) => {
            if (credentialResponse.credential) {
              onCredential(credentialResponse.credential);
            } else {
              const msg = 'Google no devolvió credencial (id_token).';
              console.error('[GoogleSignIn]', msg, credentialResponse);
              onError?.(msg);
            }
          }}
          onError={() => {
            // The library does not expose the GIS error; GIS already logs the real cause as
            // [GSI_LOGGER] in the console (for example "The given origin is not allowed").
            const msg = `No pudimos iniciar con Google. ${originHelp}`;
            console.error('[GoogleSignIn] onError — buscá el mensaje [GSI_LOGGER] en la consola.');
            onError?.(msg);
          }}
          useOneTap={false}
        />
      </div>

      {loopbackAlias && (
        <p role="alert" className="text-xs font-semibold text-error-600">
          Estás abriendo la app por <code>{origin}</code>. Google trata <code>127.0.0.1</code> como un
          origen distinto de <code>localhost</code>: abrí <code>http://localhost:5173</code> o registrá
          esa IP también.
        </p>
      )}

      {import.meta.env.DEV && (
        <p className="text-xs text-content-muted">
          Origen actual: <code>{origin}</code>. Para Google Sign-In, ese valor exacto debe figurar en
          Authorized JavaScript origins del OAuth client.
        </p>
      )}
    </div>
  );
}
