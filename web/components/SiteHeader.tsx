import Link from "next/link";
import { Brand } from "./Brand";

/** Cabeçalho do site público (server component — sem interatividade). */
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-30 border-b border-outline/60 bg-surface/90 backdrop-blur">
      <div className="container-page flex h-16 items-center justify-between">
        <Brand />
        <nav className="flex items-center gap-2 sm:gap-4">
          <Link
            href="/como-funciona/"
            className="hidden rounded-lg px-3 py-2 text-sm font-medium text-ink/80 hover:text-brand sm:inline"
          >
            Como funciona
          </Link>
          <Link
            href="/entrar/"
            className="rounded-lg px-3 py-2 text-sm font-medium text-ink/80 hover:text-brand"
          >
            Entrar
          </Link>
          <Link href="/registar/" className="btn-primary px-4 py-2 text-sm">
            Criar conta
          </Link>
        </nav>
      </div>
    </header>
  );
}
