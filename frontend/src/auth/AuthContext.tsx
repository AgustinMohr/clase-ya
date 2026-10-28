import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import {
  api,
  type LoginResponse,
  type SessionUser,
  UNAUTHORIZED_EVENT,
} from '../api';

export const GOOGLE_CLIENT_ID = (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) || '';

type AccountRole = 'STUDENT' | 'TEACHER';

interface AuthContextValue {
  user: SessionUser | null;
  /** True while the session cookie is being resolved through /api/auth/me. */
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string, role?: AccountRole) => Promise<void>;
  /** AUTH-001/SEC-04: creates a PENDING account; the user still has to open the emailed link. */
  register: (email: string, password: string, role: AccountRole, captchaToken?: string) => Promise<void>;
  activate: (token: string) => Promise<void>;
  resendActivation: (email: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Session state for the SPA. The JWT lives in an HttpOnly cookie (SEC-002 / ADR-020): it is never
 * readable here, so the user is resolved from `/api/auth/me` on load.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api
      .me()
      .then((session) => {
        if (active) setUser(session);
      })
      .catch(() => {
        // 401 simply means no session; a network failure leaves the UI logged out.
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const applySession = useCallback((response: LoginResponse) => {
    setUser(response.user);
    setLoading(false);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // The local session must end even if the server call fails.
    } finally {
      setUser(null);
      setLoading(false);
    }
  }, []);

  const register = useCallback(
    (email: string, password: string, role: AccountRole, captchaToken?: string) =>
      api.register(email, password, role, captchaToken),
    [],
  );

  const activate = useCallback((token: string) => api.activate(token), []);

  const resendActivation = useCallback((email: string) => api.resendActivation(email), []);

  useEffect(() => {
    const onUnauthorized = () => {
      setUser(null);
      setLoading(false);
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      login: async (email, password) => applySession(await api.login(email, password)),
      loginWithGoogle: async (idToken, role) => applySession(await api.loginWithGoogle(idToken, role)),
      register,
      activate,
      resendActivation,
      logout,
    }),
    [user, loading, applySession, register, activate, resendActivation, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
