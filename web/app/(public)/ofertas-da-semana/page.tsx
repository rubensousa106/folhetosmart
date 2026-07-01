import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { pageMetadata, breadcrumbJsonLd } from "@/lib/seo";
import { JsonLd } from "@/components/JsonLd";
import { WeekHighlights } from "@/components/WeekHighlights";

const PATH = "/ofertas-da-semana";

export const metadata: Metadata = pageMetadata({
  title: "Ofertas da semana dos supermercados",
  description:
    "Vê uma amostra das melhores ofertas da semana do Lidl, Continente, Pingo Doce, Intermarché e Aldi. Cria conta grátis e compara todos os preços no FolhetoSmart.",
  path: PATH,
  keywords: [
    "ofertas da semana",
    "promoções supermercado",
    "folhetos da semana",
    "ofertas supermercado",
    "promoções da semana",
  ],
});

export default function OfertasPage() {
  return (
    <article className="container-page py-12 sm:py-16">
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Início", path: "/" },
          { name: "Ofertas da semana", path: PATH },
        ])}
      />

      <header className="max-w-3xl">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          Ofertas da semana
        </h1>
        <p className="mt-4 text-lg text-ink/70">
          Uma amostra do que está em promoção esta semana nos principais supermercados
          portugueses. Para comparares <strong>todos</strong> os preços, produto a
          produto, e guardares a tua lista, cria uma conta gratuita.
        </p>
      </header>

      <div className="mt-8">
        <WeekHighlights />
      </div>

      <section className="mt-12 rounded-3xl bg-brand-gradient px-6 py-12 text-center text-white">
        <h2 className="text-2xl font-bold">Vê todas as ofertas e poupa</h2>
        <p className="mx-auto mt-3 max-w-xl text-white/80">
          Compara os folhetos do Lidl, Continente, Pingo Doce, Intermarché e Aldi e
          descobre onde cada produto está mais barato.
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
