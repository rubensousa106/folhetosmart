// Gera os ícones raster (PNG) a partir dos SVG em assets/, para o favicon, o
// apple-touch-icon e os ícones PWA (incl. maskable). Correr: node scripts/generate-icons.mjs
// Requer "sharp" (devDependency).
import sharp from "sharp";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const icon = await readFile(join(root, "assets/icon.svg"));
const maskable = await readFile(join(root, "assets/icon-maskable.svg"));
const out = (name) => join(root, "public", name);

const jobs = [
  [icon, 32, "icon-32.png"],
  [icon, 180, "apple-touch-icon.png"],
  [icon, 192, "icon-192.png"],
  [icon, 512, "icon-512.png"],
  [maskable, 512, "icon-maskable-512.png"],
];

for (const [svg, size, name] of jobs) {
  await sharp(svg, { density: 384 }).resize(size, size).png().toFile(out(name));
  console.log(`✓ ${name} (${size}px)`);
}

// Imagem Open Graph (partilha em redes sociais) — 1200x630.
const og = await readFile(join(root, "assets/og.svg"));
await sharp(og, { density: 200 }).resize(1200, 630).png().toFile(out("og.png"));
console.log("✓ og.png (1200x630)");

console.log("Ícones gerados em web/public/");
