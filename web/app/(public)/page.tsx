import Link from "next/link";
import type { Metadata } from "next";
import {
  Search,
  ShoppingCart,
  BellRing,
  Sparkles,
  Star,
  Tag,
  ArrowRight,
} from "lucide-react";
import { SITE, SUPERMARKETS } from "@/lib/site";
import { faqJsonLd } from "@/lib/seo";
import { JsonLd } from "@/components/JsonLd";
import { WeekHighlights } from "@/components/WeekHighlights";

export const metadata: Metadata = {
  title: "Comparador de preços dos folhetos de supermercado",
  description: SITE.description,
  alternates: { canonical: SITE.url },
};

const FAQ = [
  {
    q: "O que é o FolhetoSmart?",
    a: "É um comparador que reúne os folhetos semanais do Lidl, Continente, Pingo Doce, Intermarché e Aldi e mostra, produto a produto, onde está o preço mais baixo.",
  },
  {
    q: "Quando saem os folhetos novos?",
    a: "Cada supermercado lança o seu folheto no seu próprio dia da semana. O FolhetoSmart recolhe e organiza os preços automaticamente assim que cada folheto é publicado.",
  },
  {
    q: "A lista de compras fica sincronizada com a app?",
    a: "Sim. A lista é guardada na tua conta, por isso o que adicionas no site fica disponível no telemóvel e vice-versa.",
  },
  {
    q: "É gratuito?",
    a: "Sim, o FolhetoSmart é 100% gratuito, sem subscrições nem custos escondidos. Basta criares uma conta gratuita para comparares os preços.",
  },
];

const STEPS = [
  {
    icon: Search,
    title: "Pesquisa um produto",
    text: "Escreve, por exemplo, “leite meio-gordo” ou “Doritos” e vê o preço em cada supermercado.",
  },
  {
    icon: Star,
    title: "Vê o mais barato em destaque",
    text: "O preço mais baixo aparece realçado, com promoções assinaladas e a validade de cada preço.",
  },
  {
    icon: ShoppingCart,
    title: "Adiciona à tua lista",
    text: "Junta as ofertas à lista de compras — agrupada por supermercado, com o total calculado.",
  },
];

const FEATURES = [
  {
    icon: Sparkles,
    title: "Comparador inteligente com IA",
    text: "O mesmo produto tem nomes diferentes em cada folheto. A IA junta-os para comparares laranjas com laranjas.",
  },
  {
    icon: Tag,
    title: "Promoções e validade",
    text: "Vês logo o que está em promoção e até quando o preço é válido, com aviso quando está a acabar.",
  },
  {
    icon: BellRing,
    title: "Sincroniza com o telemóvel",
    text: "A tua lista vive na conta: começas no site e continuas na app Android sem perder nada.",
  },
];

export default function HomePage() {
  return (
    <>
      <JsonLd data={faqJsonLd(FAQ)} />

      {/* Hero */}
      <section className="container-page py-16 sm:py-24">
        <div className="mx-auto max-w-3xl text-center">
          <span className="inline-flex items-center gap-2 rounded-full bg-savings-bg px-4 py-1.5 text-sm font-medium text-brand-dark">
            <Sparkles className="h-4 w-4" /> Folhetos de 5 supermercados num só lugar
          </span>
          <h1 className="mt-6 text-4xl font-extrabold tracking-tight text-ink sm:text-5xl">
            Compara os folhetos e descobre onde é{" "}
            <span className="text-brand">mais barato</span>
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg text-ink/70">
            O FolhetoSmart compara automaticamente os folhetos semanais do Lidl,
            Continente, Pingo Doce, Intermarché e Aldi — e diz-te, produto a
            produto, onde compensa comprar. Poupa sempre.
          </p>
          <div className="mt-8 flex flex-col items-center justify-center gap-3 sm:flex-row">
            <Link href="/registar/" className="btn-primary w-full sm:w-auto">
              Começar gratuitamente <ArrowRight className="h-4 w-4" />
            </Link>
            <Link href="/como-funciona/" className="btn-outline w-full sm:w-auto">
              Ver como funciona
            </Link>
          </div>
          <p className="mt-4 text-sm text-ink/60">
            Já tens conta? <Link href="/entrar/" className="font-medium text-brand hover:underline">Entrar</Link>
          </p>
        </div>
      </section>

      {/* Como funciona (3 passos) */}
      <section className="container-page py-8" aria-labelledby="passos">
        <h2 id="passos" className="text-center text-2xl font-bold text-ink">
          Poupar em 3 passos
        </h2>
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {STEPS.map((s) => (
            <div key={s.title} className="rounded-2xl border border-outline/60 bg-white p-6">
              <span className="grid h-11 w-11 place-items-center rounded-xl bg-savings-bg text-brand">
                <s.icon className="h-6 w-6" />
              </span>
              <h3 className="mt-4 font-semibold text-ink">{s.title}</h3>
              <p className="mt-2 text-sm text-ink/70">{s.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Funcionalidades */}
      <section className="container-page py-16" aria-labelledby="funcionalidades">
        <h2 id="funcionalidades" className="text-center text-2xl font-bold text-ink">
          Tudo o que precisas para comprar melhor
        </h2>
        <div className="mt-10 grid gap-6 md:grid-cols-3">
          {FEATURES.map((f) => (
            <div key={f.title} className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-outline/40">
              <span className="grid h-11 w-11 place-items-center rounded-xl bg-brand/10 text-brand">
                <f.icon className="h-6 w-6" />
              </span>
              <h3 className="mt-4 font-semibold text-ink">{f.title}</h3>
              <p className="mt-2 text-sm text-ink/70">{f.text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Supermercados (ligações internas para SEO) */}
      <section className="container-page py-8" aria-labelledby="supermercados">
        <h2 id="supermercados" className="text-center text-2xl font-bold text-ink">
          Folhetos dos principais supermercados
        </h2>
        <div className="mx-auto mt-8 flex max-w-3xl flex-wrap justify-center gap-3">
          {SUPERMARKETS.map((s) => (
            <Link
              key={s.slug}
              href={`/supermercados/${s.slug}/`}
              className="rounded-full border border-outline bg-white px-5 py-2.5 font-medium text-ink transition hover:border-brand hover:text-brand"
            >
              Folheto {s.name}
            </Link>
          ))}
        </div>
        <p className="mt-6 text-center text-sm text-ink/70">
          Novo por aqui? Vê o nosso{" "}
          <Link
            href="/poupar-no-supermercado/"
            className="font-medium text-brand hover:underline"
          >
            guia para poupar no supermercado
          </Link>
          .
        </p>
      </section>

      {/* Ofertas da semana (teaser real, vem do backend) */}
      <section className="container-page py-8" aria-labelledby="ofertas">
        <h2 id="ofertas" className="text-center text-2xl font-bold text-ink">
          Ofertas da semana
        </h2>
        <p className="mx-auto mt-2 max-w-2xl text-center text-ink/70">
          Uma amostra do que está em promoção. Cria conta para comparares todos os preços.
        </p>
        <div className="mt-8">
          <WeekHighlights />
        </div>
        <div className="mt-6 text-center">
          <Link href="/ofertas-da-semana/" className="font-medium text-brand hover:underline">
            Ver mais ofertas da semana →
          </Link>
        </div>
      </section>

      {/* FAQ */}
      <section className="container-page py-16" aria-labelledby="faq">
        <h2 id="faq" className="text-center text-2xl font-bold text-ink">
          Perguntas frequentes
        </h2>
        <div className="mx-auto mt-10 max-w-3xl divide-y divide-outline/60 rounded-2xl border border-outline/60 bg-white">
          {FAQ.map((item) => (
            <details key={item.q} className="group p-6">
              <summary className="flex cursor-pointer list-none items-center justify-between font-medium text-ink">
                {item.q}
                <ArrowRight className="h-4 w-4 transition group-open:rotate-90" />
              </summary>
              <p className="mt-3 text-sm text-ink/70">{item.a}</p>
            </details>
          ))}
        </div>
      </section>

      {/* CTA final */}
      <section className="container-page pb-20">
        <div className="rounded-3xl bg-brand px-6 py-12 text-center text-white sm:py-16">
          <h2 className="text-2xl font-bold sm:text-3xl">Começa hoje a poupar</h2>
          <p className="mx-auto mt-3 max-w-xl text-white/80">
            Cria a tua conta gratuita e descobre onde está o preço mais baixo esta semana.
          </p>
          <Link
            href="/registar/"
            className="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 font-semibold text-brand transition hover:bg-white/90"
          >
            Criar conta gratuita <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </>
  );
}
