import type { Metadata } from "next";
import { SITE } from "./site";

/** Constrói metadata por página, herdando o título base e o canonical. */
export function pageMetadata(opts: {
  title: string;
  description?: string;
  path?: string;
  noindex?: boolean;
  keywords?: string[];
}): Metadata {
  const url = `${SITE.url}${opts.path ?? "/"}`;
  const description = opts.description ?? SITE.description;
  return {
    title: opts.title,
    description,
    keywords: opts.keywords ?? [...SITE.keywords],
    alternates: { canonical: url },
    robots: opts.noindex
      ? { index: false, follow: false }
      : { index: true, follow: true },
    openGraph: {
      type: "website",
      locale: "pt_PT",
      siteName: SITE.name,
      title: opts.title,
      description,
      url,
    },
    twitter: {
      card: "summary_large_image",
      title: opts.title,
      description,
    },
  };
}

/** JSON-LD: Organization + WebSite (com SearchAction) para a homepage. */
export function organizationJsonLd() {
  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "Organization",
        name: SITE.name,
        url: SITE.url,
        slogan: SITE.tagline,
        description: SITE.description,
      },
      {
        "@type": "WebSite",
        name: SITE.name,
        url: SITE.url,
        inLanguage: "pt-PT",
        potentialAction: {
          "@type": "SearchAction",
          target: `${SITE.url}/app/comparar/?q={search_term_string}`,
          "query-input": "required name=search_term_string",
        },
      },
      {
        "@type": "MobileApplication",
        name: SITE.name,
        operatingSystem: "ANDROID",
        applicationCategory: "ShoppingApplication",
        offers: { "@type": "Offer", price: "0", priceCurrency: "EUR" },
        downloadUrl: SITE.playStoreUrl,
      },
    ],
  };
}

/** JSON-LD: FAQPage para a landing (rich results). */
export function faqJsonLd(items: { q: string; a: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: items.map((it) => ({
      "@type": "Question",
      name: it.q,
      acceptedAnswer: { "@type": "Answer", text: it.a },
    })),
  };
}
