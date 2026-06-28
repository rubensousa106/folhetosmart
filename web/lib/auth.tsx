"use client";

/**
 * Estado de autenticação no cliente (Context). Mantém o "mesmo formato" da app:
 * a área /app só é acessível com sessão iniciada. A sessão vive em localStorage
 * (tokens JWT geridos em lib/api.ts).
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useRouter } from "next/navigation";
import {
  login as apiLogin,
  register as apiRegister,
  tokenStore,
  type AuthResponse,
} from "./api";

type Session = { email: string; role: string };

type AuthState = {
  ready: boolean;
  session: Session | null;
  /** Devolve a resposta (inclui must_change_password, p/ forçar nova palavra-passe). */
  login: (email: string, password: string) => Promise<AuthResponse>;
  register: (email: string, password: string) => Promise<AuthResponse>;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

/** Descodifica o payload de um JWT sem validar (só para ler email/role/exp). */
function decodeJwt(token: string): { sub?: string; role?: string; exp?: number } | null {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function sessionFromToken(): Session | null {
  const token = tokenStore.access;
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims) return null;
  if (claims.exp && claims.exp * 1000 < Date.now() && !tokenStore.refresh) {
    return null;
  }
  return { email: claims.sub ?? "", role: claims.role ?? "USER" };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setSession(sessionFromToken());
    setReady(true);
  }, []);

  const applyAuth = useCallback((res: AuthResponse) => {
    setSession({ email: res.email, role: res.role });
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await apiLogin(email, password);
      applyAuth(res);
      return res;
    },
    [applyAuth],
  );

  const register = useCallback(
    async (email: string, password: string) => {
      const res = await apiRegister(email, password);
      applyAuth(res);
      return res;
    },
    [applyAuth],
  );

  const logout = useCallback(() => {
    tokenStore.clear();
    setSession(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ ready, session, login, register, logout }),
    [ready, session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth tem de ser usado dentro de <AuthProvider>");
  return ctx;
}

/** Guarda de rota: redireciona para /entrar se não houver sessão. */
export function useRequireAuth() {
  const { ready, session } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (ready && !session) {
      router.replace("/entrar/");
    }
  }, [ready, session, router]);
  return { ready, session };
}
