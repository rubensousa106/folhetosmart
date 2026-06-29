"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Tag, ArrowRight } from "lucide-react";
import { highlights, type FlyerOffering } from "@/lib/api";

function euros(n: number) {
  return n.toLocaleString("pt-PT", { style: "currency", currency: "EUR" });
}

/** Teaser público: mostra uma amostra das ofertas da semana (vem do backend). */
export function WeekHighlights() {
  const [offers, setOffers] = useState<FlyerOffering[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    highlights()
      .then((list) => setOffers(list))
      .catch(() => setFailed(true));
  }, []);

  // Ainda sem ligação/dados → mostra um convite, não cartões partidos.
  if (failed || (offers && offers.length === 0)) {
    return (
      <div className="rounded-2xl border border-dashed border-outline bg-white p-8 text-center">
        <p className="text-ink/70">As ofertas desta semana aparecem aqui em breve.</p>
        <Link href="/registar/" className="btn-primary mt-4">
          Cria conta para comparar tudo <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    );
  }

  if (!offers) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-32 animate-pulse rounded-2xl bg-outline/20" />
        ))}
      </div>
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {offers.map((o, i) => (
        <div
          key={`${o.produto}-${o.supermercado}-${i}`}
          className="flex flex-col rounded-2xl border border-outline/60 bg-white p-4"
        >
          <span className="inline-flex w-fit items-center gap-1 rounded-full bg-savings-bg px-2.5 py-1 text-xs font-medium text-brand-dark">
            <Tag className="h-3 w-3" /> {o.supermercado}
          </span>
          <p className="mt-3 flex-1 text-sm font-medium text-ink">{o.produto}</p>
          <p className="mt-2 text-xl font-extrabold text-brand">{euros(o.preco)}</p>
          {o.validade && <p className="mt-1 text-xs text-ink/50">Válido: {o.validade}</p>}
        </div>
      ))}
    </div>
  );
}
