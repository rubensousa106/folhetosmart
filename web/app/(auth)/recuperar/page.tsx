"use client";

import { useState } from "react";
import Link from "next/link";
import { Mail, Loader2, CheckCircle2 } from "lucide-react";
import { forgotPassword, ApiError } from "@/lib/api";

export default function RecuperarPage() {
  const [email, setEmail] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await forgotPassword(email);
      setDone(true);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Não foi possível enviar o email. Tenta novamente.",
      );
    } finally {
      setLoading(false);
    }
  }

  if (done) {
    return (
      <div className="rounded-2xl border border-outline/60 bg-white p-8 text-center shadow-sm">
        <CheckCircle2 className="mx-auto h-10 w-10 text-brand" />
        <h1 className="mt-4 text-xl font-bold text-ink">Verifica o teu email</h1>
        <p className="mt-2 text-sm text-ink/70">
          Se existir uma conta associada a <strong>{email}</strong>, enviámos
          instruções para repores a palavra-passe.
        </p>
        <Link href="/entrar/" className="btn-primary mt-6 w-full">Voltar a entrar</Link>
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-outline/60 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-ink">Recuperar palavra-passe</h1>
      <p className="mt-1 text-sm text-ink/70">
        Indica o teu email e enviamos-te instruções para a repor.
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

        {error && (
          <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
        )}

        <button type="submit" className="btn-primary w-full" disabled={loading}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Mail className="h-4 w-4" />}
          Enviar instruções
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-ink/70">
        <Link href="/entrar/" className="font-medium text-brand hover:underline">Voltar a entrar</Link>
      </p>
    </div>
  );
}
