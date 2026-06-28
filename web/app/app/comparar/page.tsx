"use client";

import { useEffect, useMemo, useState } from "react";
import { Search, Star, Plus, Check, Loader2, AlertCircle } from "lucide-react";
import {
  fetchOfferings,
  shopping,
  type FlyerOffering,
  ApiError,
} from "@/lib/api";

const MAX_GROUPS = 60;

type Group = { produto: string; offers: FlyerOffering[]; best: number };

function groupOfferings(offerings: FlyerOffering[]): Group[] {
  const map = new Map<string, FlyerOffering[]>();
  for (const o of offerings) {
    const key = o.produto;
    (map.get(key) ?? map.set(key, []).get(key)!).push(o);
  }
  const groups: Group[] = [];
  for (const [produto, offers] of map) {
    offers.sort((a, b) => a.preco - b.preco);
    groups.push({ produto, offers, best: offers[0]?.preco ?? 0 });
  }
  // Mais relevantes primeiro: os que têm mais lojas a comparar.
  groups.sort((a, b) => b.offers.length - a.offers.length);
  return groups;
}

function eur(v: number) {
  return new Intl.NumberFormat("pt-PT", { style: "currency", currency: "EUR" }).format(v);
}

export default function CompararPage() {
  const [offerings, setOfferings] = useState<FlyerOffering[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [added, setAdded] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const q = params.get("q");
    if (q) setQuery(q);

    fetchOfferings()
      .then(setOfferings)
      .catch((e) =>
        setError(
          e instanceof ApiError && e.status === 401
            ? "Sessão expirada. Volta a entrar."
            : "Não foi possível carregar os folhetos desta semana.",
        ),
      )
      .finally(() => setLoading(false));
  }, []);

  const groups = useMemo(() => groupOfferings(offerings), [offerings]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const base = q
      ? groups.filter(
          (g) =>
            g.produto.toLowerCase().includes(q) ||
            g.offers.some(
              (o) =>
                o.original?.toLowerCase().includes(q) ||
                o.marca?.toLowerCase().includes(q),
            ),
        )
      : groups;
    return base.slice(0, MAX_GROUPS);
  }, [groups, query]);

  async function addToList(o: FlyerOffering) {
    const key = `${o.produto}::${o.supermercado}`;
    try {
      await shopping.add(o.produto, o.supermercado, o.preco);
      setAdded((s) => ({ ...s, [key]: true }));
      setTimeout(() => setAdded((s) => ({ ...s, [key]: false })), 1500);
    } catch {
      /* feedback silencioso; o utilizador pode tentar de novo */
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-ink">Comparar preços</h1>
      <p className="mt-1 text-sm text-ink/70">
        Pesquisa um produto e vê onde está mais barato esta semana.
      </p>

      <div className="sticky top-16 z-10 mt-4 bg-surface py-2">
        <div className="relative">
          <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-ink/40" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Ex.: leite, Doritos, café…"
            className="input pl-12"
            aria-label="Pesquisar produto"
          />
        </div>
      </div>

      {loading && (
        <div className="grid place-items-center py-20 text-ink/60">
          <Loader2 className="h-6 w-6 animate-spin text-brand" />
          <p className="mt-3 text-sm">A carregar folhetos…</p>
        </div>
      )}

      {error && !loading && (
        <div className="mt-6 flex items-start gap-3 rounded-xl bg-danger/10 p-4 text-danger">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {!loading && !error && (
        <>
          {!query && (
            <p className="mt-4 text-xs text-ink/50">
              A mostrar {filtered.length} de {groups.length} produtos. Usa a
              pesquisa para encontrares o que precisas.
            </p>
          )}

          <ul className="mt-4 space-y-4">
            {filtered.map((g) => (
              <li key={g.produto} className="rounded-2xl border border-outline/60 bg-white p-4">
                <h2 className="font-semibold text-ink">{g.produto}</h2>
                <ul className="mt-3 space-y-2">
                  {g.offers.map((o, i) => {
                    const isBest = o.preco === g.best;
                    const key = `${o.produto}::${o.supermercado}`;
                    return (
                      <li
                        key={`${o.supermercado}-${i}`}
                        className={`flex items-center justify-between gap-3 rounded-xl px-3 py-2 ${
                          isBest ? "bg-savings-bg" : "bg-surface"
                        }`}
                      >
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-ink">{o.supermercado}</span>
                            {isBest && (
                              <span className="inline-flex items-center gap-1 rounded-full bg-brand px-2 py-0.5 text-[11px] font-semibold text-white">
                                <Star className="h-3 w-3" /> Mais barato
                              </span>
                            )}
                          </div>
                          {o.validade && (
                            <p className="truncate text-xs text-ink/60">{o.validade}</p>
                          )}
                        </div>
                        <div className="flex items-center gap-3">
                          <span className={`whitespace-nowrap font-bold ${isBest ? "text-brand" : "text-ink"}`}>
                            {eur(o.preco)}
                          </span>
                          <button
                            onClick={() => addToList(o)}
                            className="grid h-9 w-9 place-items-center rounded-lg border border-outline bg-white text-brand transition hover:border-brand"
                            aria-label={`Adicionar ${o.produto} (${o.supermercado}) à lista`}
                          >
                            {added[key] ? <Check className="h-4 w-4" /> : <Plus className="h-4 w-4" />}
                          </button>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </li>
            ))}
          </ul>

          {filtered.length === 0 && (
            <p className="py-16 text-center text-ink/60">
              Sem resultados para “{query}”.
            </p>
          )}
        </>
      )}
    </div>
  );
}
