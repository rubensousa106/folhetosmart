"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Search, Star, Lock } from "lucide-react";

type Cenario = {
  query: string;
  poupas: string;
  linhas: { loja: string; preco: number }[];
};

/** Exemplos ilustrativos — o mais barato vem sempre em primeiro. */
const CENARIOS: Cenario[] = [
  {
    query: "leite meio-gordo 1L",
    poupas: "0,26 €",
    linhas: [
      { loja: "Pingo Doce", preco: 0.79 },
      { loja: "Continente", preco: 0.95 },
      { loja: "Lidl", preco: 1.05 },
    ],
  },
  {
    query: "detergente da roupa",
    poupas: "0,80 €",
    linhas: [
      { loja: "Lidl", preco: 2.99 },
      { loja: "Intermarché", preco: 3.49 },
      { loja: "Continente", preco: 3.79 },
    ],
  },
  {
    query: "peito de frango",
    poupas: "1,00 €",
    linhas: [
      { loja: "Aldi", preco: 3.49 },
      { loja: "Pingo Doce", preco: 3.99 },
      { loja: "Lidl", preco: 4.49 },
    ],
  },
];

function euros(n: number) {
  return n.toLocaleString("pt-PT", { style: "currency", currency: "EUR" });
}

/** Preço que "conta" até ao valor final quando a linha entra. */
function PrecoAnimado({ valor, ativo }: { valor: number; ativo: boolean }) {
  const [mostrado, setMostrado] = useState(valor);

  useEffect(() => {
    if (!ativo) {
      setMostrado(valor);
      return;
    }
    let raf = 0;
    const inicio = performance.now();
    const dur = 550;
    const tick = (t: number) => {
      const p = Math.min((t - inicio) / dur, 1);
      const eased = 1 - Math.pow(1 - p, 4); // ease-out-quart
      setMostrado(valor * eased);
      if (p < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [valor, ativo]);

  return <>{euros(mostrado)}</>;
}

type Fase = "typing" | "reveal" | "won";

/**
 * Demonstração viva do comparador: escreve uma pesquisa sozinha, os preços
 * entram em cascata e o mais barato "vence". O estado inicial (SSR) é o
 * cenário completo — a animação só arranca no cliente e respeita
 * prefers-reduced-motion. A última linha é o isco que leva ao registo.
 */
export function HeroLiveDemo({ className = "" }: { className?: string }) {
  const [idx, setIdx] = useState(0);
  const [typed, setTyped] = useState(CENARIOS[0].query);
  const [fase, setFase] = useState<Fase>("won");
  const timers = useRef<number[]>([]);

  useEffect(() => {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let vivo = true;
    const t = (fn: () => void, ms: number) => {
      timers.current.push(window.setTimeout(fn, ms));
    };

    const corre = (i: number) => {
      if (!vivo) return;
      const c = CENARIOS[i];
      setIdx(i);
      setFase("typing");
      setTyped("");
      c.query.split("").forEach((_, j) => {
        t(() => setTyped(c.query.slice(0, j + 1)), 250 + j * 45);
      });
      const fimTyping = 250 + c.query.length * 45;
      t(() => setFase("reveal"), fimTyping + 250);
      t(() => setFase("won"), fimTyping + 1250);
      t(() => corre((i + 1) % CENARIOS.length), fimTyping + 5200);
    };

    // Deixa o hero-reveal terminar antes de a demo ganhar vida.
    t(() => corre(1), 2400);

    return () => {
      vivo = false;
      timers.current.forEach(clearTimeout);
      timers.current = [];
    };
  }, []);

  const c = CENARIOS[idx];
  const escondido = fase === "typing";
  const venceu = fase === "won";
  const [melhor, ...outros] = c.linhas;

  return (
    <div className={`relative ${className}`}>
      {/* Selo de poupança — salta quando o mais barato vence. */}
      <div
        className={`absolute -right-2 -top-4 z-10 rounded-full bg-brand px-4 py-2 text-sm font-bold text-white shadow-elevated ring-4 ring-white transition duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] ${
          venceu ? "scale-100 opacity-100" : "scale-50 opacity-0"
        }`}
        aria-hidden="true"
      >
        Poupas {c.poupas}
      </div>

      <div className="rounded-2xl bg-white p-5 shadow-floating">
        <div
          className="flex items-center gap-2 rounded-xl bg-surface px-4 py-3 ring-1 ring-outline/60"
          aria-hidden="true"
        >
          <Search className="h-4 w-4 shrink-0 text-ink/40" />
          <span className="truncate text-sm text-ink">
            {typed}
            {fase === "typing" && <span className="demo-caret" />}
          </span>
        </div>

        <ul className="mt-4 space-y-2" aria-hidden="true">
          <li
            className={`flex items-center justify-between gap-3 rounded-xl px-4 py-3 ring-1 transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${
              venceu ? "bg-savings-bg ring-brand/30" : "bg-white ring-outline/50"
            } ${escondido ? "translate-y-2 opacity-0" : "translate-y-0 opacity-100"}`}
            style={{ transitionDelay: escondido ? "0ms" : "0ms" }}
          >
            <span className="flex items-center gap-2.5">
              <Star
                className={`h-4 w-4 shrink-0 fill-brand text-brand transition-transform duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] ${
                  venceu ? "scale-100" : "scale-0"
                }`}
              />
              <span>
                <span className="block text-sm font-semibold text-ink">{melhor.loja}</span>
                <span
                  className={`block text-xs font-medium text-brand-dark transition-opacity duration-300 ${
                    venceu ? "opacity-100" : "opacity-0"
                  }`}
                >
                  Mais barato esta semana
                </span>
              </span>
            </span>
            <span
              className={`text-lg font-extrabold transition-colors duration-300 ${
                venceu ? "text-brand-dark" : "text-ink/70"
              }`}
            >
              <PrecoAnimado valor={melhor.preco} ativo={!escondido} />
            </span>
          </li>
          {outros.map((linha, i) => (
            <li
              key={`${idx}-${linha.loja}`}
              className={`flex items-center justify-between gap-3 rounded-xl px-4 py-3 ring-1 ring-outline/50 transition-all duration-500 ease-[cubic-bezier(0.22,1,0.36,1)] ${
                escondido ? "translate-y-2 opacity-0" : "translate-y-0 opacity-100"
              }`}
              style={{ transitionDelay: escondido ? "0ms" : `${(i + 1) * 110}ms` }}
            >
              <span className="text-sm font-medium text-ink/80">{linha.loja}</span>
              <span className="font-semibold text-ink/70">
                <PrecoAnimado valor={linha.preco} ativo={!escondido} />
              </span>
            </li>
          ))}
        </ul>

        <Link
          href="/registar/"
          className="mt-3 flex items-center justify-between gap-3 rounded-xl border border-dashed border-outline bg-surface px-4 py-3 text-sm transition hover:border-brand"
        >
          <span className="flex items-center gap-2 text-ink/60">
            <Lock className="h-4 w-4 shrink-0" aria-hidden="true" /> +2 supermercados
          </span>
          <span className="font-semibold text-brand">Criar conta para ver</span>
        </Link>
      </div>

      <p className="mt-3 text-center text-xs text-white/70">
        Exemplo ilustrativo — os preços vêm dos folhetos oficiais de cada semana.
      </p>
    </div>
  );
}
