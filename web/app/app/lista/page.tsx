"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  Minus,
  Plus,
  Trash2,
  Loader2,
  ShoppingCart,
  AlertCircle,
} from "lucide-react";
import { shopping, type ShoppingItem, ApiError } from "@/lib/api";

function eur(v: number) {
  return new Intl.NumberFormat("pt-PT", { style: "currency", currency: "EUR" }).format(v);
}

export default function ListaPage() {
  const [items, setItems] = useState<ShoppingItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<Record<string, boolean>>({});

  useEffect(() => {
    shopping
      .list()
      .then(setItems)
      .catch((e) =>
        setError(
          e instanceof ApiError && e.status === 401
            ? "Sessão expirada. Volta a entrar."
            : "Não foi possível carregar a tua lista.",
        ),
      )
      .finally(() => setLoading(false));
  }, []);

  const groups = useMemo(() => {
    const map = new Map<string, ShoppingItem[]>();
    for (const it of items) {
      (map.get(it.supermercado) ?? map.set(it.supermercado, []).get(it.supermercado)!).push(it);
    }
    return Array.from(map.entries())
      .map(([supermercado, list]) => ({
        supermercado,
        list,
        total: list.reduce((sum, it) => sum + it.preco * it.quantity, 0),
      }))
      .sort((a, b) => a.supermercado.localeCompare(b.supermercado));
  }, [items]);

  const grandTotal = useMemo(
    () => items.reduce((sum, it) => sum + it.preco * it.quantity, 0),
    [items],
  );

  async function changeQty(item: ShoppingItem, delta: number) {
    const next = Math.max(1, item.quantity + delta);
    if (next === item.quantity) return;
    setBusy((b) => ({ ...b, [item.id]: true }));
    // Atualização otimista.
    setItems((list) => list.map((it) => (it.id === item.id ? { ...it, quantity: next } : it)));
    try {
      await shopping.setQuantity(item.id, next);
    } catch {
      setItems((list) => list.map((it) => (it.id === item.id ? { ...it, quantity: item.quantity } : it)));
    } finally {
      setBusy((b) => ({ ...b, [item.id]: false }));
    }
  }

  async function removeItem(item: ShoppingItem) {
    const snapshot = items;
    setItems((list) => list.filter((it) => it.id !== item.id));
    try {
      await shopping.remove(item.id);
    } catch {
      setItems(snapshot);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-ink">A minha lista</h1>
      <p className="mt-1 text-sm text-ink/70">
        Sincronizada com a tua conta — disponível também no telemóvel.
      </p>

      {loading && (
        <div className="grid place-items-center py-20 text-ink/60">
          <Loader2 className="h-6 w-6 animate-spin text-brand" />
        </div>
      )}

      {error && !loading && (
        <div className="mt-6 flex items-start gap-3 rounded-xl bg-danger/10 p-4 text-danger">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0" />
          <p className="text-sm">{error}</p>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="mt-10 grid place-items-center rounded-2xl border border-dashed border-outline bg-white py-16 text-center">
          <ShoppingCart className="h-10 w-10 text-ink/30" />
          <p className="mt-4 font-medium text-ink">A tua lista está vazia</p>
          <p className="mt-1 text-sm text-ink/60">
            Adiciona produtos a partir do Comparar.
          </p>
          <Link href="/app/comparar/" className="btn-primary mt-5">Ir comparar</Link>
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <div className="mt-6 space-y-5">
            {groups.map((g) => (
              <section key={g.supermercado} className="rounded-2xl border border-outline/60 bg-white">
                <header className="flex items-center justify-between border-b border-outline/60 px-4 py-3">
                  <h2 className="font-semibold text-ink">{g.supermercado}</h2>
                  <span className="text-sm font-semibold text-brand">{eur(g.total)}</span>
                </header>
                <ul className="divide-y divide-outline/40">
                  {g.list.map((it) => (
                    <li key={it.id} className="flex items-center gap-3 px-4 py-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-ink">{it.produto}</p>
                        <p className="text-xs text-ink/60">{eur(it.preco)} / un.</p>
                      </div>
                      <div className="flex items-center gap-1 rounded-lg border border-outline">
                        <button
                          onClick={() => changeQty(it, -1)}
                          disabled={busy[it.id] || it.quantity <= 1}
                          className="grid h-8 w-8 place-items-center text-ink/70 disabled:opacity-40"
                          aria-label="Diminuir quantidade"
                        >
                          <Minus className="h-4 w-4" />
                        </button>
                        <span className="w-6 text-center text-sm font-semibold">{it.quantity}</span>
                        <button
                          onClick={() => changeQty(it, 1)}
                          disabled={busy[it.id]}
                          className="grid h-8 w-8 place-items-center text-ink/70 disabled:opacity-40"
                          aria-label="Aumentar quantidade"
                        >
                          <Plus className="h-4 w-4" />
                        </button>
                      </div>
                      <span className="w-20 text-right font-semibold text-ink">
                        {eur(it.preco * it.quantity)}
                      </span>
                      <button
                        onClick={() => removeItem(it)}
                        className="grid h-8 w-8 place-items-center text-ink/40 hover:text-danger"
                        aria-label="Remover item"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>

          <div className="mt-6 flex items-center justify-between rounded-2xl bg-brand px-5 py-4 text-white">
            <span className="font-medium">Total</span>
            <span className="text-xl font-bold">{eur(grandTotal)}</span>
          </div>
        </>
      )}
    </div>
  );
}
