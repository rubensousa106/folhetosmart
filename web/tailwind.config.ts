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
    },
  },
  plugins: [],
};

export default config;
