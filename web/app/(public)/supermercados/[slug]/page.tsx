import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ArrowRight } from "lucide-react";
import { SITE, SUPERMARKETS, getSupermarket } from "@/lib/site";
import { pageMetadata, faqJsonLd, breadcrumbJsonLd } from "@/lib/seo";
import { JsonLd } from "@/components/JsonLd";
import { WeekHighlights } from "@/components/WeekHighlights";

/** Gera uma página estática por supermercado (SEO por palavra-chave). */
export function generateStaticParams() {
  return SUPERMARKETS.map((s) => ({ slug: s.slug }));
}

export function generateMetadata({
  params,
}: {
  params: { slug: string };
}): Metadata {
  const sm = getSupermarket(params.slug);
  if (!sm) return {};
  return pageMetadata({
    title: `${sm.name}: comparar preços e promoções`,
    description: `${sm.blurb} Compara os preços do folheto ${sm.name} desta semana com Lidl, Continente, Pingo Doce, Intermarché e Aldi no FolhetoSmart.`,
    path: `/supermercados/${sm.slug}`,
    keywords: [
      `folheto ${sm.name}`,
      `promoções ${sm.name}`,
      `${sm.name} preços`,
      "comparador de preços",
      "folhetos supermercado",
    ],
  });
}

export default function SupermercadoPage({
  params,
}: {
  params: { slug: string };
}) {
  const sm = getSupermarket(params.slug);
  if (!sm) notFound();

  const others = SUPERMARKETS.filter((s) => s.slug !== sm.slug);
  const faq = [
    {
      q: `Quando sai o folheto ${sm.name}?`,
      a: `O folheto ${sm.name} sai habitualmente à ${sm.flyerDay}. O FolhetoSmart organiza os preços dessa semana automaticamente.`,
    },
    {
      q: `Posso comparar o ${sm.name} com outros supermercados?`,
      a: `Sim. O FolhetoSmart compara o ${sm.name} com Lidl, Continente, Pingo Doce, Intermarché e Aldi, produto a produto, mostrando o preço mais baixo em destaque.`,
    },
    {
      q: `Comparar o folheto ${sm.name} no FolhetoSmart é grátis?`,
      a: `Sim, é grátis. Cria uma conta gratuita e compara os preços do folheto ${sm.name} com os outros supermercados sempre que precisares.`,
    },
  ];

  return (
    <>
      <JsonLd data={faqJsonLd(faq)} />
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Início", path: "/" },
          { name: sm.name, path: `/supermercados/${sm.slug}` },
        ])}
      />
      <div className="container-page max-w-3xl py-12 sm:py-16">
        <nav className="text-sm text-ink/60">
          <Link href="/" className="hover:text-brand">Início</Link>
          <span className="px-2">/</span>
          <span>{sm.name}</span>
        </nav>

        <h1 className="mt-4 text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          {sm.name}: comparar preços e promoções
        </h1>
        <p className="mt-4 text-lg text-ink/70">{sm.blurb}</p>

        <div className="card-elevated mt-10 rounded-2xl bg-savings-bg p-6">
          <h2 className="text-lg font-bold text-brand-dark">
            Compara o {sm.name} com os outros supermercados
          </h2>
          <p className="mt-2 text-sm text-ink/70">
            Cria conta e pesquisa qualquer produto para ver, lado a lado, onde está
            o preço mais baixo esta semana.
          </p>
          <Link href="/registar/" className="btn-primary mt-4">
            Comparar preços agora <ArrowRight className="h-4 w-4" />
          </Link>
          <p className="mt-4 text-sm text-ink/70">
            Vê também o nosso{" "}
            <Link
              href="/poupar-no-supermercado/"
              className="font-medium text-brand hover:underline"
            >
              guia para poupar no supermercado
            </Link>{" "}
            com 7 dicas práticas.
          </p>
        </div>

        <section className="mt-12">
          <h2 className="text-xl font-bold text-ink">Algumas ofertas desta semana</h2>
          <p className="mt-2 text-sm text-ink/70">
            Uma amostra do que está em promoção. Cria conta para veres todos os preços
            e comparares o {sm.name} com os outros supermercados.
          </p>
          <div className="mt-6">
            <WeekHighlights />
          </div>
        </section>

        <section className="mt-12">
          <h2 className="text-xl font-bold text-ink">Perguntas frequentes</h2>
          <div className="mt-4 divide-y divide-outline/60 rounded-2xl border border-outline/60 bg-white">
            {faq.map((item) => (
              <details key={item.q} className="group p-5">
                <summary className="flex cursor-pointer list-none items-center justify-between font-medium text-ink">
                  {item.q}
                  <ArrowRight className="h-4 w-4 transition group-open:rotate-90" />
                </summary>
                <p className="mt-3 text-sm text-ink/70">{item.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="mt-12">
          <h2 className="text-xl font-bold text-ink">Outros supermercados</h2>
          <div className="mt-4 flex flex-wrap gap-3">
            {others.map((o) => (
              <Link
                key={o.slug}
                href={`/supermercados/${o.slug}/`}
                className="rounded-full border border-outline bg-white px-4 py-2 text-sm font-medium text-ink hover:border-brand hover:text-brand"
              >
                {o.name}
              </Link>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}
