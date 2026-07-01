import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { pageMetadata, breadcrumbJsonLd } from "@/lib/seo";
import { JsonLd } from "@/components/JsonLd";
import { SavingsCalculator } from "@/components/SavingsCalculator";

const PATH = "/calculadora-poupanca";

export const metadata: Metadata = pageMetadata({
  title: "Calculadora de poupança no supermercado",
  description:
    "Calcula em segundos quanto podes poupar por ano a comprar onde está mais barato. Compara os folhetos do Lidl, Continente, Pingo Doce, Intermarché e Aldi com o FolhetoSmart.",
  path: PATH,
  keywords: [
    "calculadora de poupança",
    "quanto poupo no supermercado",
    "poupar no supermercado",
    "calcular poupança compras",
    "comparar preços supermercado",
  ],
});

export default function CalculadoraPage() {
  return (
    <article className="container-page max-w-3xl py-12 sm:py-16">
      <JsonLd
        data={breadcrumbJsonLd([
          { name: "Início", path: "/" },
          { name: "Calculadora de poupança", path: PATH },
        ])}
      />

      <nav className="text-sm text-ink/60" aria-label="Caminho">
        <Link href="/" className="hover:text-brand">Início</Link>
        <span className="mx-2">/</span>
        <span className="text-ink/80">Calculadora de poupança</span>
      </nav>

      <header className="mt-6">
        <h1 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          Quanto podes poupar no supermercado?
        </h1>
        <p className="mt-4 text-lg text-ink/70">
          Arrasta e descobre a tua poupança estimada por ano só por comprares cada
          produto onde está mais barato. Depois, deixa o FolhetoSmart fazer as contas
          por ti todas as semanas.
        </p>
      </header>

      <div className="mt-8">
        <SavingsCalculator />
      </div>

      <section className="mt-12">
        <h2 className="text-xl font-bold text-ink">Como é que se poupa tanto?</h2>
        <p className="mt-3 text-ink/70">
          O mesmo produto custa preços diferentes em cada cadeia, e as promoções mudam
          todas as semanas. Ao comparar os folhetos do Lidl, Continente, Pingo Doce,
          Intermarché e Aldi e comprar cada coisa onde está mais barata, a fatura desce
          — sobretudo nos frescos e nos produtos em promoção. Vê o nosso{" "}
          <Link href="/poupar-no-supermercado/" className="font-medium text-brand hover:underline">
            guia para poupar no supermercado
          </Link>{" "}
          com 7 dicas práticas.
        </p>
      </section>

      <section className="mt-10 rounded-3xl bg-brand-gradient px-6 py-12 text-center text-white">
        <h2 className="text-2xl font-bold">Transforma a estimativa em poupança real</h2>
        <p className="mx-auto mt-3 max-w-xl text-white/80">
          Cria a tua conta gratuita e compara os preços reais desta semana em segundos.
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
