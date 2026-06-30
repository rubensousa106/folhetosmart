"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { UserPlus, Loader2 } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { ApiError, users } from "@/lib/api";
import { DistritoCidadeFields } from "@/components/DistritoCidadeFields";

export default function RegistarPage() {
  const { register } = useAuth();
  const router = useRouter();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [district, setDistrict] = useState("");
  const [city, setCity] = useState("");
  const [accepted, setAccepted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!name.trim()) {
      setError("O nome é obrigatório.");
      return;
    }
    if (password.length < 8) {
      setError("A palavra-passe tem de ter pelo menos 8 caracteres.");
      return;
    }
    if (!district) {
      setError("O distrito é obrigatório.");
      return;
    }
    if (!city) {
      setError("A cidade é obrigatória.");
      return;
    }
    if (!accepted) {
      setError("Tens de aceitar os Termos e a Política de Privacidade.");
      return;
    }
    setLoading(true);
    try {
      await register(email, password);
      // Guarda o nome logo a seguir (a conta já tem sessão) — como na app.
      try {
        await users.updateProfile(name.trim(), district, city);
      } catch {
        /* não bloqueia o registo; o nome pode ser definido na conta */
      }
      router.replace("/app/comparar/");
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "Não foi possível criar a conta. Tenta novamente.",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="rounded-2xl border border-outline/60 bg-white p-8 shadow-sm">
      <h1 className="text-2xl font-bold text-ink">Criar conta</h1>
      <p className="mt-1 text-sm text-ink/70">
        Gratuito. Começa hoje a poupar em cada ida às compras.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <div>
          <label htmlFor="name" className="mb-1 block text-sm font-medium text-ink">Nome completo</label>
          <input
            id="name" type="text" autoComplete="name" required
            className="input" value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
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
            id="password" type="password" autoComplete="new-password" required minLength={8}
            className="input" value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <p className="mt-1 text-xs text-ink/60">Pelo menos 8 caracteres.</p>
        </div>

        <DistritoCidadeFields
          distrito={district}
          cidade={city}
          onDistritoChange={(v) => { setDistrict(v); setCity(""); }}
          onCidadeChange={setCity}
          required
        />
        <p className="-mt-2 text-xs text-ink/50">A zona define o folheto regional do Aldi.</p>

        <label className="flex items-start gap-2 text-sm text-ink/80">
          <input
            type="checkbox" className="mt-1 h-4 w-4 accent-brand"
            checked={accepted} onChange={(e) => setAccepted(e.target.checked)}
          />
          <span>
            Aceito os <Link href="/termos/" className="text-brand hover:underline">Termos</Link> e a{" "}
            <Link href="/privacidade/" className="text-brand hover:underline">Política de Privacidade</Link>.
          </span>
        </label>

        {error && (
          <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">{error}</p>
        )}

        <button type="submit" className="btn-primary w-full" disabled={loading}>
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />}
          Criar conta gratuita
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-ink/70">
        Já tens conta?{" "}
        <Link href="/entrar/" className="font-medium text-brand hover:underline">Entrar</Link>
      </p>
    </div>
  );
}
