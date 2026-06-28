/** @type {import('next').NextConfig} */
const nextConfig = {
  // Exportação estática: o site público é HTML pré-renderizado (ótimo para SEO)
  // e a área autenticada (/app/*) corre no cliente. Deploy simples no Cloudflare
  // Pages (apenas ficheiros estáticos).
  output: "export",
  reactStrictMode: true,
  trailingSlash: true,
  images: {
    // Exportação estática não usa o otimizador de imagens do Next.
    unoptimized: true,
  },
};

export default nextConfig;
