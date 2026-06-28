import { readFile } from "node:fs/promises";
import { join } from "node:path";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";

/**
 * Renderiza um documento legal a partir do markdown em `legal/` (raiz do repo),
 * em build-time. Remove comentários HTML de instruções internas.
 */
export async function LegalContent({ file }: { file: string }) {
  const path = join(process.cwd(), "..", "legal", file);
  const raw = await readFile(path, "utf8");
  const cleaned = raw.replace(/<!--[\s\S]*?-->/g, "").trim();
  return (
    <div className="legal">
      <Markdown remarkPlugins={[remarkGfm]}>{cleaned}</Markdown>
    </div>
  );
}
