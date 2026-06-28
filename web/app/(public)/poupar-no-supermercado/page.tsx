import Link from "next/link";
import type { Metadata } from "next";
import {
  Search,
  ListChecks,
  Tag,
  Scale,
  CalendarDays,
  Boxes,
  Sparkles,
  ArrowRight,
} from "lucide-react";
import { SITE, SUPERMARKETS } from "@/lib/site";
import { pageMetadata, breadcrumbJsonLd, faqJsonLd } from "@/lib/seo";
import { JsonLd } from "@/components/JsonLd";

const PATH = "/poupar-no-supermercado";

export const metadata: Metadata = pageMetadata({
  title: "Como poupar no supermercado: 7 dicas práticas (guia 2026)",
  description:
    "Guia prático para poupar nas compras: compara os folhetos do Lidl, Continente, Pingo Doce, Intermarché e Aldi, faz a lista certa e compra ao melhor preço todas as semanas.",
  path: PATH,
  keywords: [
    "poupar no supermercado",
    "poupar nas compras",
    "como poupar nas compras",
    "comparar preços supermercado",
    "folhetos da semana",
    "promoções supermercado",
    "preço por quilo",
    "marcas próprias",
  ],
});

const TIPS = [
  {
    icon: Search,
    title: "1. Compara os folhetos antes de ires às compras",
    text: "O mesmo produto pode custar bem menos noutra cadeia. Em vez de abrires cinco folhetos, o FolhetoSmart junta o Lidl, Continente, Pingo Doce, Intermarché e Aldi e mostra-te, produto a produto, onde está o preço mais baixo desta semana.",
  },
  {
    icon: ListChecks,
    title: "2. Faz a lista de compras — e segue-a",
    text: "Quem leva lista gasta menos e desperdiça menos. Adiciona as ofertas à tua lista no site ou na app: fica agrupada por supermercado e com o total calculado, para saberes quanto vais gastar antes de chegar à caixa.",
  },
  {
    icon: Scale,
    title: "3. Olha para o preço por quilo (ou por litro)",
    text: "A embalagem maior nem sempre compensa. Compara o preço por kg/litro — é a forma justa de comparar tamanhos e marcas diferentes do mesmo produto.",
  },
  {
    icon: Tag,
    title: "4. Aproveita as promoções certas (não todas)",
    text: "Uma promoção só poupa se for de algo que ias comprar na mesma. Foca-te nos produtos da tua lista que estão em desconto esta semana e ignora as 'pechinchas' que não precisas.",
  },
  {
    icon: Boxes,
    title: "5. Marcas próprias vs. marcas nacionais",
    text: "As marcas próprias dos supermercados são, muitas vezes, mais baratas com qualidade equivalente. Vale a pena experimentar nos produtos básicos (arroz, massa, leite, conservas).",
  },
  {
    icon: CalendarDays,
    title: "6. Conhece o calendário dos folhetos",
    text: "Os folhetos mudam ao longo da semana (o Aldi de segunda a domingo, o Continente e o Pingo Doce de terça a segunda). Comprar no início da validade garante-te o stock das melhores ofertas.",
  },
  {
    icon: Sparkles,
    title: "7. Deixa a IA juntar os preços por ti",
    text: "O mesmo produto tem nomes diferentes em cada folheto. O FolhetoSmart usa IA para os juntar, para comparares 'laranjas com laranjas' e veres logo a opção mais barata — sem fazer contas.",
  },
];

const FAQ = [
  {
    q: "Qual é a melhor forma de poupar no supermercado?",
    a: "Comparar os folhetos da semana antes de ir às compras e levar uma lista. Assim compras cada produto onde está mais barato e evitas gastos por impulso. O FolhetoSmart faz essa comparação automaticamente.",
  },
  {
    q: "Os folhetos de que supermercados são comparados?",
    a: "Lidl, Continente, Pingo Doce, Intermarché e Aldi — as principais cadeias em Portugal.",
  },
  {
    q: "Quanto posso poupar?",
    a: "Depende das compras, mas comprar cada produto na cadeia mais barata da semana pode reduzir bastante a fatura, sobretudo nos frescos e nos produtos em promoção.",
  },
  {
    q: "O FolhetoSmart é gratuito?",
    a: "Sim. Basta criar uma conta gratuita para comparares os preços e guardares a tua lista de compras.",
  },
];

export default function PouparPage() {
  return (
    <article className="container-page py-12 sm:py-16">
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Início", path: "/" },
          { name: "Poupar no supermercado", path: PATH },
        ])}
      />
      <JsonLd data={faqJsonLd(FAQ)} />

      <nav className="text-sm text-ink/60" aria-label="Caminho">
        <Link href="/" className="hover:text-brand">Início</Link>
        <span className="mx-2">/</span>
        <span className="text-ink/80">Poupar no supermercado</span>
      </nav>

      <header className="mt-6 max-w-3xl">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          Como poupar no supermercado: 7 dicas práticas
        </h1>
        <p className="mt-4 text-lg text-ink/70">
          Poupar nas compras não é comprar menos — é comprar melhor. Reunimos as
          dicas que mais fazem diferença na fatura, e mostramos como o
          FolhetoSmart compara os folhetos do Lidl, Continente, Pingo Doce,
          Intermarché e Aldi para te dizer, produto a produto, onde é mais barato.
        </p>
      </header>

      <div className="mt-10 grid gap-6 md:grid-cols-2">
        {TIPS.map((t) => (
          <section key={t.title} className="rounded-2xl border border-outline/60 bg-white p-6">
            <span className="grid h-11 w-11 place-items-center rounded-xl bg-savings-bg text-brand">
              <t.icon className="h-6 w-6" />
            </span>
            <h2 className="mt-4 text-lg font-semibold text-ink">{t.title}</h2>
            <p className="mt-2 text-sm leading-relaxed text-ink/70">{t.text}</p>
          </section>
        ))}
      </div>

      {/* Ligações internas para SEO */}
      <section className="mt-12" aria-labelledby="folhetos">
        <h2 id="folhetos" className="text-xl font-bold text-ink">
          Compara os folhetos por supermercado
        </h2>
        <div className="mt-4 flex flex-wrap gap-3">
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
      </section>

      {/* FAQ */}
      <section className="mt-12" aria-labelledby="faq">
        <h2 id="faq" className="text-xl font-bold text-ink">Perguntas frequentes</h2>
        <div className="mt-6 max-w-3xl divide-y divide-outline/60 rounded-2xl border border-outline/60 bg-white">
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

      {/* CTA */}
      <section className="mt-12 rounded-3xl bg-brand px-6 py-12 text-center text-white">
        <h2 className="text-2xl font-bold">Começa a poupar esta semana</h2>
        <p className="mx-auto mt-3 max-w-xl text-white/80">
          Cria a tua conta gratuita e compara os folhetos de {SITE.name} em segundos.
        </p>
        <Link
          href="/registar/"
          className="mt-6 inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 font-semibold text-brand transition hover:bg-white/90"
        >
          Criar conta gratuita <ArrowRight className="h-4 w-4" />
        </Link>
      </section>
    </article>
  );
}
