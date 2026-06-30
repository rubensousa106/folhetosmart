/**
 * Avatar determinístico (animal + cor) a partir de uma semente estável (o email).
 *
 * ESPELHA EXATAMENTE a app Android (com.folhetosmart.ui.UserAvatar): o mesmo
 * conjunto de animais/cores e o MESMO hash (java.lang.String.hashCode) → o mesmo
 * utilizador vê o mesmo animal na app e na web.
 */

const AVATAR_ANIMAIS = [
  "🦊", "🐱", "🐶", "🐼", "🐨", "🦁", "🐯", "🐸", "🐵", "🦉", "🦅", "🐧",
  "🐢", "🐙", "🦋", "🐝", "🦄", "🐬", "🐳", "🦎", "🦒", "🦓", "🐰", "🐻",
  "🐷", "🐮", "🦔", "🦦", "🐹", "🦫",
];

// As mesmas cores da app (0xFFRRGGBB → #RRGGBB).
const AVATAR_CORES = [
  "#EF9A9A", "#F48FB1", "#CE93D8", "#9FA8DA", "#90CAF9", "#80DEEA",
  "#A5D6A7", "#FFCC80", "#BCAAA4", "#B0BEC5", "#80CBC4", "#FFAB91",
];

/** Replica java.lang.String.hashCode() (int de 32 bits, com overflow). */
function javaHashCode(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    // Math.imul + "| 0" mantêm a aritmética em 32 bits com sinal, como em Java.
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}

/** Igual a Math.floorMod do Java (resto sempre não-negativo). */
const floorMod = (a: number, n: number) => ((a % n) + n) % n;

/** (animal, cor) determinísticos para a semente dada — idêntico ao avatarFor da app. */
export function avatarFor(seed: string): { animal: string; color: string } {
  const h = javaHashCode(seed);
  const animal = AVATAR_ANIMAIS[floorMod(h, AVATAR_ANIMAIS.length)];
  // Em Java, "h / 31" é divisão inteira (trunca para zero) — Math.trunc faz o mesmo.
  const color = AVATAR_CORES[floorMod(Math.trunc(h / 31), AVATAR_CORES.length)];
  return { animal, color };
}
