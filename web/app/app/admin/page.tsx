"use client";

import { useEffect, useState } from "react";
import { Loader2, CheckCircle2, XCircle, ShieldAlert } from "lucide-react";
import { admin, ApiError, type FlyersStatus } from "@/lib/api";
import { useAuth } from "@/lib/auth";

function fmt(dt: string | null) {
  if (!dt) return "—";
  const d = new Date(dt);
  return isNaN(d.getTime())
    ? "—"
    : d.toLocaleString("pt-PT", { dateStyle: "short", timeStyle: "short" });
}

/** Painel mínimo de administração: estado (só-leitura) dos folhetos da semana. */
export default function AdminPage() {
  const { session } = useAuth();
  const [data, setData] = useState<FlyersStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    admin
      .flyersStatus()
      .then(setData)
      .catch((e) =>
        setError(
          e instanceof ApiError && (e.status === 403 || e.status === 401)
            ? "forbidden"
            : "Não foi possível carregar o estado dos folhetos.",
        ),
      )
      .finally(() => setLoading(false));
  }, []);

  // Guarda no cliente (o backend é a verdadeira barreira: devolve 403 a não-admins).
  if ((session && session.role !== "ADMIN") || error === "forbidden") {
    return (
      <div className="mx-auto max-w-md rounded-2xl border border-outline/60 bg-white p-8 text-center">
        <ShieldAlert className="mx-auto h-10 w-10 text-ink/40" />
        <h1 className="mt-3 text-xl font-bold text-ink">Área de administração</h1>
        <p className="mt-2 text-sm text-ink/60">Esta área é reservada à administração.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="grid place-items-center py-20">
        <Loader2 className="h-6 w-6 animate-spin text-brand" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-ink">Administração</h1>
        {data && <p className="mt-1 text-sm text-ink/70">Folhetos da semana: {data.week}</p>}
      </div>

      {error && (
        <p className="rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger" role="alert">
          {error}
        </p>
      )}

      {data && (
        <section className="overflow-x-auto rounded-2xl border border-outline/60 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-surface text-left text-ink/60">
              <tr>
                <th className="px-4 py-3 font-medium">Supermercado</th>
                <th className="px-4 py-3 font-medium">Folheto</th>
                <th className="px-4 py-3 text-right font-medium">Produtos</th>
                <th className="px-4 py-3 font-medium">Atualizado</th>
              </tr>
            </thead>
            <tbody>
              {data.supermarkets.map((s) => (
                <tr key={s.slug} className="border-t border-outline/60">
                  <td className="px-4 py-3 font-medium text-ink">{s.name}</td>
                  <td className="px-4 py-3">
                    {s.has_flyer ? (
                      <span className="inline-flex items-center gap-1 text-brand">
                        <CheckCircle2 className="h-4 w-4" /> Sim
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 text-ink/40">
                        <XCircle className="h-4 w-4" /> Não
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums text-ink">
                    {s.products_imported}
                  </td>
                  <td className="px-4 py-3 text-ink/70">{fmt(s.synced_at)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      <p className="text-xs text-ink/50">
        Apenas leitura. O carregamento de folhetos continua a fazer-se pela app.
      </p>
    </div>
  );
}
