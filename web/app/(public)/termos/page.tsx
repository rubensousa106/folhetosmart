import type { Metadata } from "next";
import { pageMetadata } from "@/lib/seo";
import { LegalContent } from "@/components/LegalContent";

export const metadata: Metadata = pageMetadata({
  title: "Termos de Serviço",
  description:
    "Condições de utilização do FolhetoSmart — serviço gratuito de comparação de preços de folhetos.",
  path: "/termos",
});

export default function TermosPage() {
  return (
    <article className="container-page max-w-3xl py-12 sm:py-16">
      <LegalContent file="terms_of_service.md" />
    </article>
  );
}
