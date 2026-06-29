import type { MetadataRoute } from "next";
import { SITE, SUPERMARKETS } from "@/lib/site";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  const staticPaths = [
    "",
    "/como-funciona",
    "/poupar-no-supermercado",
    "/calculadora-poupanca",
    "/ofertas-da-semana",
    "/privacidade",
    "/termos",
    "/entrar",
    "/registar",
  ];

  const pages: MetadataRoute.Sitemap = staticPaths.map((p) => ({
    url: `${SITE.url}${p}`,
    lastModified: now,
    changeFrequency: p === "" ? "daily" : "monthly",
    priority: p === "" ? 1 : 0.7,
  }));

  const supermarketPages: MetadataRoute.Sitemap = SUPERMARKETS.map((s) => ({
    url: `${SITE.url}/supermercados/${s.slug}`,
    lastModified: now,
    changeFrequency: "weekly",
    priority: 0.8,
  }));

  return [...pages, ...supermarketPages];
}
