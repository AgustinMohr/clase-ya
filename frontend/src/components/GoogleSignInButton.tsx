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
 */
export default function GoogleSignInButton({ onCredential, onError, disabled }: Props) {
  if (!GOOGLE_CLIENT_ID) {
    return (
      <p className="hint">
        Login con Google no configurado: definí <code>VITE_GOOGLE_CLIENT_ID</code> para habilitarlo.
      </p>
    );
  }
  return (
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
          // La librería no expone el detalle; GIS ya imprime la causa real
          // como [GSI_LOGGER] (p. ej. "The given origin is not allowed...").
          const msg = 'No pudimos iniciar con Google. Revisá la consola ([GSI_LOGGER]) para el detalle.';
          console.error('[GoogleSignIn] onError — buscá el mensaje [GSI_LOGGER] en la consola.');
          onError?.(msg);
        }}
        useOneTap={false}
      />
    </div>
  );
}
