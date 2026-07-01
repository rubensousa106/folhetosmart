import type { Config } from "tailwindcss";

/**
 * Paleta alinhada com a app Android (ui/theme/Color.kt) para a identidade
 * visual ser consistente entre a web e o telemóvel.
 */
const config: Config = {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "./lib/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: "#2E7D32", // verde primário
          light: "#4CAF50",
          dark: "#1B5E20",
        },
        savings: { bg: "#E8F5E9" },
        promo: { bg: "#FFF3E0", text: "#E65100" },
        best: { bg: "#E3F2FD", text: "#1565C0" },
        danger: "#B3261E",
        surface: "#FCFDF7",
        ink: "#1A1C19",
        outline: "#C2C8BC",
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
      },
      // Sombras com tom de marca (em vez de cinza genérico) — usadas com
      // moderação em cards de destaque (ver .card-elevated em globals.css).
      boxShadow: {
        elevated:
          "0 4px 16px -2px rgba(27,94,32,0.12), 0 2px 6px -2px rgba(27,94,32,0.08)",
        floating:
          "0 16px 40px -8px rgba(27,94,32,0.20), 0 6px 16px -4px rgba(27,94,32,0.10)",
      },
      // Gradiente subtil de marca — só para heros/CTAs finais, não em tudo.
      backgroundImage: {
        "brand-gradient": "linear-gradient(135deg, #2E7D32 0%, #1B5E20 100%)",
      },
    },
  },
  plugins: [],
};

export default config;
