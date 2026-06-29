// Configuração da publicidade (Google AdSense).
// O publisher ID é público (aparece no script de todas as páginas e no ads.txt).
export const ADSENSE_CLIENT = "ca-pub-6792201256188159";

// IDs dos blocos de anúncio. Cria-os no AdSense (Anúncios → Por bloco de anúncios)
// e cola aqui o "data-ad-slot" de cada um. Enquanto estiverem vazios, o anúncio
// NÃO é renderizado (não parte nada — a base já fica pronta).
export const AD_SLOTS = {
  /** Skyscraper vertical à esquerda (só em ecrãs largos). */
  railLeft: "",
  /** Skyscraper vertical à direita (só em ecrãs largos). */
  railRight: "",
  /** Banner horizontal dentro do /app (só para utilizadores USER, não ADMIN). */
  appInline: "",
};
