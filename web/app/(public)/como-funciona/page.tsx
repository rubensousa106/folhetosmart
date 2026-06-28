import Link from "next/link";
import type { Metadata } from "next";
import { ArrowRight, Search, Star, ShoppingCart, RefreshCw, Sparkles } from "lucide-react";
import { pageMetadata } from "@/lib/seo";

export const metadata: Metadata = pageMetadata({
  title: "Como funciona",
  description:
    "Como o FolhetoSmart compara os folhetos semanais dos supermercados portugueses com IA e mostra onde é mais barato.",
  path: "/como-funciona",
});

const SECTIONS = [
  {
    icon: RefreshCw,
    title: "Recolhemos os folhetos por ti",
    text: "Todas as quintas-feiras, quando os supermercados publicam os folhetos da semana, recolhemos e organizamos os preços do Lidl, Continente, Pingo Doce, Intermarché e Aldi.",
  },
  {
    icon: Sparkles,
    title: "A IA junta o mesmo produto entre lojas",
    text: "“Doritos Chilli 150g” num folheto, “Doritos Tortilla Spicy 150g” noutro — a inteligência artificial percebe que é o mesmo produto e junta os preços na mesma comparação.",
  },
  {
    icon: Search,
    title: "Pesquisas e comparas num instante",
    text: "Escreves o nome de um produto e vês de imediato o preço em cada supermercado, com promoções assinaladas e a validade de cada preço.",
  },
  {
    icon: Star,
    title: "O mais barato fica em destaque",
    text: "O preço mais baixo da semana aparece realçado, para saberes logo onde compensa comprar.",
  },
  {
    icon: ShoppingCart,
    title: "A tua lista anda contigo",
    text: "Adicionas as ofertas à lista de compras, agrupada por supermercado. Como fica guardada na tua conta, continuas no telemóvel exatamente onde paraste no site.",
  },
];

export default function ComoFuncionaPage() {
  return (
    <div className="container-page max-w-3xl py-12 sm:py-16">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
        Como funciona o FolhetoSmart
      </h1>
      <p className="mt-4 text-lg text-ink/70">
        Processamos os folhetos uma vez por semana e tu lês o resultado já
        preparado — rápido, organizado e sempre com o preço mais baixo em destaque.
      </p>

      <ol className="mt-10 space-y-6">
        {SECTIONS.map((s, i) => (
          <li key={s.title} className="flex gap-4 rounded-2xl border border-outline/60 bg-white p-6">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-savings-bg text-brand">
              <s.icon className="h-6 w-6" />
            </span>
            <div>
              <h2 className="font-semibold text-ink">
                {i + 1}. {s.title}
              </h2>
              <p className="mt-2 text-sm text-ink/70">{s.text}</p>
            </div>
          </li>
        ))}
      </ol>

      <div className="mt-10 flex flex-col items-center gap-3 rounded-2xl bg-brand p-8 text-center text-white">
        <h2 className="text-xl font-bold">Pronto para poupar?</h2>
        <p className="max-w-md text-white/80">
          Cria a tua conta gratuita e acede já à comparação de preços desta semana.
        </p>
        <Link
          href="/registar/"
          className="mt-2 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 font-semibold text-brand hover:bg-white/90"
        >
          Criar conta gratuita <ArrowRight className="h-4 w-4" />
        </Link>
      </div>
    </div>
  );
}
