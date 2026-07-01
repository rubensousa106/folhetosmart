import Link from "next/link";
import { SITE } from "@/lib/site";

/**
 * Marca: duas etiquetas de preço sobrepostas — a da frente (maior, sólida)
 * "vence" a de trás (mais pequena, translúcida). Representa o que o
 * FolhetoSmart faz: compara preços e mostra qual é o mais barato. Substitui
 * o antigo ícone de carrinho (genérico — "isto é uma loja", não "comparamos
 * preços por ti"). Assume sempre fundo `bg-brand` (#2E7D32) à volta — por
 * isso o "furo" da etiqueta da frente usa essa cor diretamente.
 */
function CompareTagMark({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden="true">
      <path d="M11 6 14 3 21 3 21 9 14 9Z" fill="currentColor" fillOpacity="0.45" />
      <path d="M6.5 13 10 9.5 19 9.5 19 17 10 17Z" fill="currentColor" />
      <circle cx="8.6" cy="13" r="1.15" fill="#2E7D32" />
    </svg>
  );
}

/** Logótipo + nome da marca. Liga à homepage por omissão; dentro da app, passa-se
 *  href="/app/comparar/" para o logo NÃO sair da área autenticada. */
export function Brand({ className = "", href = "/" }: { className?: string; href?: string }) {
  return (
    <Link
      href={href}
      className={`inline-flex items-center gap-2 font-bold text-brand ${className}`}
      aria-label={`${SITE.name} — ${SITE.tagline}`}
    >
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand text-white">
        <CompareTagMark className="h-5 w-5" />
      </span>
      <span className="text-lg tracking-tight">{SITE.name}</span>
    </Link>
  );
}
