"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowRight } from "lucide-react";

// Estimativa de poupança ao comprar cada produto onde está mais barato.
const RATE = 0.15;

function euros(n: number) {
  return n.toLocaleString("pt-PT", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0,
  });
}

export function SavingsCalculator() {
  const [weekly, setWeekly] = useState(100);
  const monthly = weekly * 4.33 * RATE;
  const yearly = weekly * 52 * RATE;

  return (
    <div className="rounded-3xl border border-outline/60 bg-white p-6 shadow-sm sm:p-8">
      <label htmlFor="gasto-semanal" className="block text-sm font-medium text-ink">
        Quanto gastas por semana no supermercado?
      </label>
      <div className="mt-3 flex items-baseline gap-2">
        <span className="text-4xl font-extrabold text-brand">{euros(weekly)}</span>
        <span className="text-sm text-ink/60">por semana</span>
      </div>
      <input
        id="gasto-semanal"
        type="range"
        min={20}
        max={300}
        step={5}
        value={weekly}
        onChange={(e) => setWeekly(Number(e.target.value))}
        className="mt-4 w-full accent-brand"
        aria-label="Gasto semanal no supermercado"
      />
      <div className="mt-2 flex justify-between text-xs text-ink/50">
        <span>20 €</span>
        <span>300 €</span>
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-2">
        <div className="rounded-2xl bg-savings-bg p-5 text-center">
          <p className="text-sm text-ink/60">Poupança por mês</p>
          <p className="mt-1 text-2xl font-bold text-brand-dark">{euros(monthly)}</p>
        </div>
        <div className="rounded-2xl bg-brand p-5 text-center text-white">
          <p className="text-sm text-white/80">Poupança por ano</p>
          <p className="mt-1 text-3xl font-extrabold">{euros(yearly)}</p>
        </div>
      </div>

      <Link href="/registar/" className="btn-primary mt-6">
        Começar a poupar — é grátis <ArrowRight className="h-4 w-4" />
      </Link>
      <p className="mt-3 text-xs text-ink/50">
        Estimativa ilustrativa (cerca de {Math.round(RATE * 100)}% ao comprar cada
        produto onde está mais barato). A poupança real depende das tuas compras.
      </p>
    </div>
  );
}
