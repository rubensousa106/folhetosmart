import Link from "next/link";
import { ArrowRight, Smartphone } from "lucide-react";
import { SITE } from "@/lib/site";

/**
 * Promoção cruzada: leva a app contigo. Tom de continuidade entre
 * dispositivos (mesma conta, mesma lista) — não "descarrega já!!".
 * iOS é mencionado sem data (não controlamos quando vai sair).
 */
export function AppPromoSection() {
  return (
    <section className="container-page py-12" aria-labelledby="app-promo">
      <div className="card-elevated grid items-center gap-10 rounded-3xl bg-brand-gradient p-8 text-white sm:p-12 lg:grid-cols-[1.1fr_0.9fr]">
        <div>
          <h2 id="app-promo" className="text-2xl font-bold sm:text-3xl">
            Leva o FolhetoSmart contigo
          </h2>
          <p className="mt-3 max-w-md text-white/85">
            A tua lista e os teus alertas ficam guardados na tua conta — o que
            adicionas no site aparece logo no telemóvel, e vice-versa.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-4">
            <Link
              href={SITE.playStoreUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 rounded-xl bg-white px-5 py-3 font-semibold text-brand-dark shadow-sm transition hover:bg-white/90"
            >
              Disponível no Google Play
              <ArrowRight className="h-4 w-4" />
            </Link>
            <span className="inline-flex items-center gap-2 text-sm text-white/75">
              <Smartphone className="h-4 w-4" />
              Brevemente também no iPhone
            </span>
          </div>
        </div>
        <AppPromoMockup className="mx-auto h-auto w-full max-w-[200px]" />
      </div>
    </section>
  );
}

/** Mini-mockup do "Comparar": linhas de preço, a mais barata em destaque. */
function AppPromoMockup({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 220 320" className={className} aria-hidden="true">
      <rect x="4" y="4" width="212" height="312" rx="28" fill="white" fillOpacity="0.12" />
      <rect x="16" y="20" width="188" height="280" rx="18" fill="white" />
      <rect x="32" y="44" width="120" height="14" rx="7" fill="#C2C8BC" />
      <rect x="32" y="86" width="156" height="48" rx="12" fill="#F3F5F0" />
      <rect x="32" y="146" width="156" height="48" rx="12" fill="#F3F5F0" />
      <rect x="32" y="206" width="156" height="52" rx="12" fill="#E8F5E9" stroke="#2E7D32" strokeWidth="2" />
      <rect x="46" y="222" width="60" height="8" rx="4" fill="#2E7D32" />
      <rect x="46" y="238" width="40" height="6" rx="3" fill="#4CAF50" />
    </svg>
  );
}
