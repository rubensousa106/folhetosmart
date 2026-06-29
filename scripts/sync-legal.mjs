// FONTE ÚNICA dos documentos legais.
//
// O site (web/) lê diretamente o markdown em legal/*.md. A app Android mostra
// texto simples de res/raw/*.txt. Este script GERA esses .txt a partir do mesmo
// markdown — por isso basta editar legal/*.md e correr:
//
//     node scripts/sync-legal.mjs
//
// ...para atualizar a app e o site a partir de um único sítio.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const legalDir = join(root, "legal");
const rawDir = join(root, "android", "app", "src", "main", "res", "raw");

const docs = ["terms_of_service", "privacy_policy"];

const stripBold = (s) => s.replace(/\*\*(.*?)\*\*/g, "$1");
const isSeparator = (s) => /^\s*\|[\s:|-]+\|\s*$/.test(s);

/** Converte o markdown dos legais para o texto simples que a app mostra. */
function mdToPlain(md) {
  const lines = md.replace(/<!--[\s\S]*?-->/g, "").split("\n");
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i].replace(/\r$/, "");
    const next = (lines[i + 1] || "").replace(/\r$/, "");

    // Tabelas markdown -> bullets "col — col — col" (salta cabeçalho e separador).
    if (/^\s*\|.*\|\s*$/.test(l)) {
      if (isSeparator(l) || isSeparator(next)) continue;
      const cells = l.split("|").slice(1, -1).map((c) => stripBold(c.trim()));
      out.push("• " + cells.join(" — "));
      continue;
    }
    if (/^---+\s*$/.test(l)) continue; // régua horizontal

    const heading = l.match(/^#{1,6}\s+(.*)$/);
    if (heading) {
      out.push(stripBold(heading[1]).toUpperCase()); // títulos -> MAIÚSCULAS
      continue;
    }

    out.push(
      stripBold(
        l.replace(/^>\s?/, "")        // blockquote
          .replace(/^(\s*)[-*]\s+/, "$1• "), // listas
      ),
    );
  }
  // Remove qualquer negrito restante (inclui **...** que abrange várias linhas).
  return out.join("\n").replace(/\*\*/g, "").replace(/\n{3,}/g, "\n\n").trim() + "\n";
}

for (const name of docs) {
  const md = await readFile(join(legalDir, `${name}.md`), "utf8");
  await writeFile(join(rawDir, `${name}.txt`), mdToPlain(md), "utf8");
  console.log(`✓ ${name}.txt (app) <- legal/${name}.md`);
}
console.log("Legais sincronizados a partir da fonte única (legal/*.md).");
