import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, clearToken, getToken, LoginResponse, setToken, UNAUTHORIZED_EVENT } from '../api';

export const GOOGLE_CLIENT_ID = (import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined) || '';

export interface SessionUser {
  id?: string;
  email?: string;
  name?: string;
  role?: string;
}

interface AuthContextValue {
  user: SessionUser | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  loginWithGoogle: (idToken: string, role?: 'STUDENT' | 'TEACHER') => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function decodeUser(token: string): SessionUser {
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return { id: json.sub, email: json.email, role: json.role, name: json.email };
  } catch {
    return {};
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken());
  const [user, setUser] = useState<SessionUser | null>(() => {
    const t = getToken();
    return t ? decodeUser(t) : null;
  });

  const applySession = useCallback((response: LoginResponse) => {
    setToken(response.accessToken);
    setTokenState(response.accessToken);
    setUser(decodeUser(response.accessToken));
  }, []);

  const logout = useCallback(() => {
    clearToken();
    setTokenState(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const onUnauthorized = () => logout();
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [logout]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      login: async (email, password) => applySession(await api.login(email, password)),
      loginWithGoogle: async (idToken, role) => applySession(await api.loginWithGoogle(idToken, role)),
      logout,
    }),
    [user, token, applySession, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
