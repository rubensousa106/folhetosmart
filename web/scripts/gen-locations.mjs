// Gera web/lib/locations.ts a partir dos ficheiros da app Android (FONTE ÚNICA):
//   android/app/src/main/java/com/folhetosmart/ui/{Distritos,Concelhos,Regioes}.kt
// Assim a web e a app usam EXATAMENTE os mesmos distritos/concelhos/regiões — muda
// nos .kt e corre `node web/scripts/gen-locations.mjs` para a web ficar igual.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const webRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const uiDir = join(
  webRoot, "..", "android", "app", "src", "main", "java", "com", "folhetosmart", "ui",
);

const distritosKt = await readFile(join(uiDir, "Distritos.kt"), "utf8");
const concelhosKt = await readFile(join(uiDir, "Concelhos.kt"), "utf8");
const regioesKt = await readFile(join(uiDir, "Regioes.kt"), "utf8");

// DISTRITOS_PT — todas as strings do listOf(...) (não há mais aspas no ficheiro).
const distritos = [...distritosKt.matchAll(/"([^"]+)"/g)].map((m) => m[1]);

// CONCELHOS_POR_DISTRITO — "Distrito" to listOf( "c1", "c2", … ),  (linha a linha,
// para nomes com parênteses como "Calheta (S. Jorge)" não partirem a análise).
const concelhos = {};
let atual = null;
for (const linha of concelhosKt.split("\n")) {
  const cab = linha.match(/^\s*"([^"]+)"\s+to\s+listOf\(/);
  if (cab) {
    atual = cab[1];
    concelhos[atual] = [];
    const resto = linha.slice(linha.indexOf("listOf(") + "listOf(".length);
    for (const m of resto.matchAll(/"([^"]+)"/g)) concelhos[atual].push(m[1]);
    continue;
  }
  if (atual) {
    for (const m of linha.matchAll(/"([^"]+)"/g)) concelhos[atual].push(m[1]);
    if (/^\s*\)/.test(linha)) atual = null; // linha que começa com ) fecha o listOf
  }
}

// REGIAO_POR_DISTRITO — pares "Distrito" to "Regiao".
const regioes = {};
for (const m of regioesKt.matchAll(/"([^"]+)"\s+to\s+"([^"]+)"/g)) regioes[m[1]] = m[2];

const j = (v) => JSON.stringify(v, null, 2);
const out = `// ⚠️ GERADO automaticamente a partir da app Android (FONTE ÚNICA) — NÃO editar à mão.
//   Fonte: android/app/src/main/java/com/folhetosmart/ui/{Distritos,Concelhos,Regioes}.kt
//   Regenerar:  node web/scripts/gen-locations.mjs
// Os distritos/concelhos/regiões são EXATAMENTE iguais aos da app.

/** Distritos + regiões autónomas (mesma ordem da app). */
export const DISTRITOS_PT: string[] = ${j(distritos)};

/** Concelhos por distrito — dropdown dependente "Distrito → Cidade". */
export const CONCELHOS_POR_DISTRITO: Record<string, string[]> = ${j(concelhos)};

/** Distrito → região do folheto regional do Aldi (Norte/Centro/Lisboa/Sul/Algarve/Açores/Madeira). */
export const REGIAO_POR_DISTRITO: Record<string, string> = ${j(regioes)};

/** Região do distrito do utilizador, ou null se não definido/desconhecido. */
export function regiaoDoDistrito(distrito: string | null | undefined): string | null {
  return distrito ? REGIAO_POR_DISTRITO[distrito] ?? null : null;
}
`;

await writeFile(join(webRoot, "lib", "locations.ts"), out, "utf8");
const totalConcelhos = Object.values(concelhos).reduce((n, l) => n + l.length, 0);
console.log(
  `OK web/lib/locations.ts -> ${distritos.length} distritos, ` +
    `${totalConcelhos} concelhos, ${Object.keys(regioes).length} regioes.`,
);
