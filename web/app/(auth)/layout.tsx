import Link from "next/link";
import { Brand } from "@/components/Brand";

/** Layout dos ecrãs de autenticação — centrado, sem indexação relevante. */
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <header className="container-page flex h-16 items-center">
        <Brand />
      </header>
      <main className="flex flex-1 items-center justify-center px-4 py-10">
        <div className="w-full max-w-md">{children}</div>
      </main>
      <footer className="container-page py-6 text-center text-xs text-ink/60">
        <Link href="/privacidade/" className="hover:text-brand">Privacidade</Link>
        <span className="px-2">·</span>
        <Link href="/termos/" className="hover:text-brand">Termos</Link>
      </footer>
    </div>
  );
}
