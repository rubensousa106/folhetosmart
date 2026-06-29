/** Configuração central do site (SEO, marca, dados de supermercados). */

/**
 * Backend REST. Vem de NEXT_PUBLIC_API_URL no build; se essa variável faltar OU
 * vier vazia (ex.: criada sem valor no CI), recorremos ao backend de produção.
 * Com `??` a string vazia "passava" — e os pedidos iam para uma URL relativa, ou
 * seja, para o próprio site estático (Cloudflare Pages), que responde 405 a um
 * POST. Removemos também a "/" final para não duplicar barras nos caminhos.
 */
const ENV_API_URL = process.env.NEXT_PUBLIC_API_URL?.trim();
const API_URL = (ENV_API_URL && ENV_API_URL.length > 0
  ? ENV_API_URL
  : "https://folhetosmart.onrender.com"
).replace(/\/+$/, "");

export const SITE = {
  name: "FolhetoSmart",
  tagline: "Poupa sempre",
  /** URL público canónico (atualizar quando o domínio estiver ligado). */
  url: "https://folhetosmart.pt",
  /** Backend REST (ver API_URL acima). */
  apiUrl: API_URL,
  locale: "pt-PT",
  themeColor: "#2E7D32",
  playStoreUrl: "https://play.google.com/store/apps/details?id=pt.folhetosmart.app",
  description:
    "Compara os preços dos folhetos semanais do Lidl, Continente, Pingo Doce, Intermarché e Aldi. O FolhetoSmart mostra-te, produto a produto, onde é mais barato — e poupas em cada ida às compras.",
  keywords: [
    "folhetos",
    "folhetos supermercado",
    "comparador de preços",
    "promoções supermercado",
    "poupar nas compras",
    "lista de compras",
    "Lidl",
    "Continente",
    "Pingo Doce",
    "Intermarché",
    "Aldi",
    "preços supermercado Portugal",
    "folhetos da semana",
  ],
} as const;

export type Supermarket = {
  slug: string;
  name: string;
  /** Dia habitual de saída do folheto. */
  flyerDay: string;
  blurb: string;
};

/** Os 5 supermercados suportados (espelha o backend/Supermarket). */
export const SUPERMARKETS: Supermarket[] = [
  {
    slug: "lidl",
    name: "Lidl",
    flyerDay: "segunda-feira",
    blurb:
      "Vê o folheto Lidl da semana e compara os preços com os outros supermercados antes de ires às compras.",
  },
  {
    slug: "continente",
    name: "Continente",
    flyerDay: "terça-feira",
    blurb:
      "Promoções e folheto Continente comparados com Lidl, Pingo Doce, Intermarché e Aldi, produto a produto.",
  },
  {
    slug: "pingo-doce",
    name: "Pingo Doce",
    flyerDay: "terça-feira",
    blurb:
      "Folheto Pingo Doce da semana com o preço mais baixo em destaque face às outras cadeias.",
  },
  {
    slug: "intermarche",
    name: "Intermarché",
    flyerDay: "quinta-feira",
    blurb:
      "Compara o folheto Intermarché com os restantes supermercados e descobre onde compensa comprar.",
  },
  {
    slug: "aldi",
    name: "Aldi",
    flyerDay: "segunda-feira",
    blurb:
      "Folheto Aldi (com versões regionais) lado a lado com Lidl, Continente, Pingo Doce e Intermarché.",
  },
];

export function getSupermarket(slug: string): Supermarket | undefined {
  return SUPERMARKETS.find((s) => s.slug === slug);
}
