import type { Metadata } from "next";
import { AppShell } from "@/components/AppShell";

/** Área autenticada — nunca indexada pelos motores de busca. */
export const metadata: Metadata = {
  robots: { index: false, follow: false },
};

export default function AppAreaLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AppShell>{children}</AppShell>;
}
