import Link from "next/link";
import { Brand } from "./Brand";
import { SITE, SUPERMARKETS } from "@/lib/site";

/** Rodapé com ligações internas (bom para SEO/maillha de links). */
export function SiteFooter() {
  return (
    <footer className="mt-20 border-t border-outline/60 bg-white">
      <div className="container-page grid gap-8 py-12 sm:grid-cols-2 lg:grid-cols-4">
        <div className="space-y-3">
          <Brand />
          <p className="max-w-xs text-sm text-ink/70">
            Compara os folhetos semanais dos supermercados portugueses e poupa em
            cada ida às compras.
          </p>
        </div>

        <nav aria-label="Supermercados">
          <h2 className="mb-3 text-sm font-semibold text-ink">Supermercados</h2>
          <ul className="space-y-2 text-sm text-ink/70">
            {SUPERMARKETS.map((s) => (
              <li key={s.slug}>
                <Link href={`/supermercados/${s.slug}/`} className="hover:text-brand">
                  Folheto {s.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav aria-label="Produto">
          <h2 className="mb-3 text-sm font-semibold text-ink">Produto</h2>
          <ul className="space-y-2 text-sm text-ink/70">
            <li><Link href="/como-funciona/" className="hover:text-brand">Como funciona</Link></li>
            <li><Link href="/poupar-no-supermercado/" className="hover:text-brand">Como poupar no supermercado</Link></li>
            <li><Link href="/calculadora-poupanca/" className="hover:text-brand">Calculadora de poupança</Link></li>
            <li><Link href="/ofertas-da-semana/" className="hover:text-brand">Ofertas da semana</Link></li>
            <li><Link href="/entrar/" className="hover:text-brand">Entrar</Link></li>
            <li><Link href="/registar/" className="hover:text-brand">Criar conta</Link></li>
            <li>
              <a href={SITE.playStoreUrl} className="hover:text-brand" rel="noopener">
                App Android
              </a>
            </li>
          </ul>
        </nav>

        <nav aria-label="Legal">
          <h2 className="mb-3 text-sm font-semibold text-ink">Legal</h2>
          <ul className="space-y-2 text-sm text-ink/70">
            <li><Link href="/privacidade/" className="hover:text-brand">Política de Privacidade</Link></li>
            <li><Link href="/termos/" className="hover:text-brand">Termos de Serviço</Link></li>
          </ul>
        </nav>
      </div>

      <div className="border-t border-outline/60">
        <div className="container-page flex flex-col items-center justify-between gap-2 py-6 text-xs text-ink/60 sm:flex-row">
          <p>© {new Date().getFullYear()} {SITE.name}. Todos os direitos reservados.</p>
          <p>Preços meramente informativos — confirma sempre na loja.</p>
        </div>
      </div>
    </footer>
  );
}
