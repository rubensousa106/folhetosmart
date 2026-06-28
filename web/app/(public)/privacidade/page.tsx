import type { Metadata } from "next";
import { pageMetadata } from "@/lib/seo";
import { LegalContent } from "@/components/LegalContent";

export const metadata: Metadata = pageMetadata({
  title: "Política de Privacidade",
  description:
    "Como o FolhetoSmart recolhe, usa e protege os teus dados. Conformidade com o RGPD.",
  path: "/privacidade",
});

export default function PrivacidadePage() {
  return (
    <article className="container-page max-w-3xl py-12 sm:py-16">
      <LegalContent file="privacy_policy.md" />
    </article>
  );
}
