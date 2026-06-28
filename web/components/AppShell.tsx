"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Search, ListChecks, User, LogOut, Loader2 } from "lucide-react";
import { Brand } from "./Brand";
import { useAuth, useRequireAuth } from "@/lib/auth";

const NAV = [
  { href: "/app/comparar/", label: "Comparar", icon: Search },
  { href: "/app/lista/", label: "Lista", icon: ListChecks },
  { href: "/app/conta/", label: "Conta", icon: User },
];

/** Casca da área autenticada: guarda de rota + navegação + sessão. */
export function AppShell({ children }: { children: React.ReactNode }) {
  const { ready, session } = useRequireAuth();
  const { logout } = useAuth();
  const pathname = usePathname();

  if (!ready || !session) {
    return (
      <div className="grid min-h-screen place-items-center bg-surface">
        <Loader2 className="h-6 w-6 animate-spin text-brand" />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <header className="sticky top-0 z-30 border-b border-outline/60 bg-surface/90 backdrop-blur">
        <div className="container-page flex h-16 items-center justify-between">
          <Brand />
          <nav className="hidden items-center gap-1 sm:flex">
            {NAV.map((n) => {
              const active = pathname.startsWith(n.href);
              return (
                <Link
                  key={n.href}
                  href={n.href}
                  className={`inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${
                    active ? "bg-brand/10 text-brand" : "text-ink/70 hover:text-brand"
                  }`}
                >
                  <n.icon className="h-4 w-4" />
                  {n.label}
                </Link>
              );
            })}
          </nav>
          <button
            onClick={logout}
            className="inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-ink/70 hover:text-danger"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden sm:inline">Sair</span>
          </button>
        </div>
      </header>

      <main className="container-page flex-1 py-6">{children}</main>

      {/* Navegação inferior (mobile) */}
      <nav className="sticky bottom-0 z-30 grid grid-cols-3 border-t border-outline/60 bg-white sm:hidden">
        {NAV.map((n) => {
          const active = pathname.startsWith(n.href);
          return (
            <Link
              key={n.href}
              href={n.href}
              className={`flex flex-col items-center gap-1 py-3 text-xs font-medium ${
                active ? "text-brand" : "text-ink/60"
              }`}
            >
              <n.icon className="h-5 w-5" />
              {n.label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
