import { ShoppingCart } from "lucide-react";
import Link from "next/link";
import { SITE } from "@/lib/site";

/** Logótipo + nome da marca, ligado à homepage. */
export function Brand({ className = "" }: { className?: string }) {
  return (
    <Link
      href="/"
      className={`inline-flex items-center gap-2 font-bold text-brand ${className}`}
      aria-label={`${SITE.name} — ${SITE.tagline}`}
    >
      <span className="grid h-9 w-9 place-items-center rounded-xl bg-brand text-white">
        <ShoppingCart className="h-5 w-5" strokeWidth={2.4} />
      </span>
      <span className="text-lg tracking-tight">{SITE.name}</span>
    </Link>
  );
}
