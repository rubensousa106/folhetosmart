import { SiteHeader } from "@/components/SiteHeader";
import { SiteFooter } from "@/components/SiteFooter";
import { AdUnit } from "@/components/AdSense";
import { AD_SLOTS } from "@/lib/ads";

/** Layout das páginas públicas (indexáveis): cabeçalho + rodapé. */
export default function PublicLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />

      {/* Anúncios laterais — só em ecrãs largos, onde há margem fora do conteúdo */}
      <aside
        className="fixed left-2 top-1/2 z-10 hidden -translate-y-1/2 xl:block"
        aria-hidden="true"
      >
        <AdUnit
          slot={AD_SLOTS.railLeft}
          format="vertical"
          responsive={false}
          style={{ width: 160, height: 600 }}
        />
      </aside>
      <aside
        className="fixed right-2 top-1/2 z-10 hidden -translate-y-1/2 xl:block"
        aria-hidden="true"
      >
        <AdUnit
          slot={AD_SLOTS.railRight}
          format="vertical"
          responsive={false}
          style={{ width: 160, height: 600 }}
        />
      </aside>

      <main className="flex-1">{children}</main>
      <SiteFooter />
    </div>
  );
}
