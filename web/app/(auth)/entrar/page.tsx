"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { LogIn, Loader2, KeyRound } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { ApiError, users } from "@/lib/api";

export default function EntrarPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Passo "definir nova palavra-passe" (entrou com palavra-passe temporária).
  const [mustChange, setMustChange] = useState(false);
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await login(email, password);
      if (res.must_change_password) {
        setMustChange(true); // mantém `password` (a temporária) para o changePassword
      } else {
        router.replace("/app/comparar/");
      }
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Não foi possível iniciar sessão. Tenta novamente.",
      );
    } finally {
      setLoading(false);
    }
  }

  async function onSetNewPassword(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (newPw.length < 8) {
      setError("A palavra-passe tem de ter pelo menos 8 caracteres.");
      return;
    }
    if (newPw !== confirmPw) {
      setError("As palavras-passe não coincidem.");
      return;
    }
    setLoading(true);
    try {
      await users.changePassword(password, newPw);
      router.replace("/app/comparar/");
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Não foi possível definir a nova palavra-passe.",
      );
    } finally {
      setLoading(false);
    }
  }

  if (mustChange) {
    return (
      <div className="rounded-2xl border border-outline/60 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-ink">Definir nova palavra-passe</h1>
        <p className="mt-1 text-sm text-ink/70">
          Entraste com uma palavra-passe temporária. Define agora a tua nova palavra-passe.
        </p>

        <form onSubmit={onSetNewPassword} className="mt-6 space-y-4">
          <div>
            <label htmlFor="newpw" className="mb-1 block text-sm font-medium text-ink">
              Nova palavra-passe (mín. 8)
            </label>
            <input
              id="newpw" type="password" autoComplete="new-password" required
              className="input" value={newPw}
              onChange={(e) => setNewPw(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="confirmpw" className="mb-1 block text-sm font-medium text-ink">
              Confirmar palavra-passe
            </label>
            <input
              id="confirmpw" type="password" autoComplete="new-password" required
              className="input" value={confirmPw}
              onChange={(e) => setConfirmPw(e.target.value)}
            />
          </div>

          {error && (
            <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
          )}

          <button type="submit" className="btn-primary w-full" disabled={loading}>
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <KeyRound className="h-4 w-4" />}
            Guardar e entrar
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-outline/60 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-ink">Entrar</h1>
      <p className="mt-1 text-sm text-ink/70">
        Acede à comparação de preços e à tua lista de compras.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="email" className="mb-1 block text-sm font-medium text-ink">Email</label>
          <input
            id="email" type="email" autoComplete="email" required
            className="input" value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="password" className="mb-1 block text-sm font-medium text-ink">Palavra-passe</label>
          <input
            id="password" type="password" autoComplete="current-password" required
            className="input" value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        {error && (
          <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
        )}

        <button type="submit" className="btn-primary w-full" disabled={loading}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <LogIn className="h-4 w-4" />}
          Entrar
        </button>
      </form>

      <div className="mt-4 flex items-center justify-between text-sm">
        <Link href="/recuperar/" className="text-ink/70 hover:text-brand">Esqueci-me da palavra-passe</Link>
        <Link href="/registar/" className="font-medium text-brand hover:underline">Criar conta</Link>
      </div>
    </div>
  );
}
