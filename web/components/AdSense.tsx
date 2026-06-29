"use client";

import Script from "next/script";
import { useEffect } from "react";
import { ADSENSE_CLIENT } from "@/lib/ads";

declare global {
  interface Window {
    adsbygoogle?: unknown[];
  }
}

/** Carrega o script do AdSense uma vez (usado no layout raiz). */
export function AdScript() {
  if (!ADSENSE_CLIENT) return null;
  return (
    <Script
      id="adsbygoogle-init"
      strategy="afterInteractive"
      crossOrigin="anonymous"
      src={`https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${ADSENSE_CLIENT}`}
    />
  );
}

/**
 * Um bloco de anúncio AdSense. Não renderiza nada enquanto o `slot` estiver vazio
 * (assim a base fica pronta sem mostrar caixas partidas antes de criares os blocos).
 */
export function AdUnit({
  slot,
  className,
  style,
  format = "auto",
  responsive = true,
}: {
  slot: string;
  className?: string;
  style?: React.CSSProperties;
  format?: string;
  responsive?: boolean;
}) {
  useEffect(() => {
    if (!slot) return;
    try {
      (window.adsbygoogle = window.adsbygoogle || []).push({});
    } catch {
      // o script ainda não carregou; ignora
    }
  }, [slot]);

  if (!ADSENSE_CLIENT || !slot) return null;

  return (
    <ins
      className={`adsbygoogle ${className ?? ""}`}
      style={{ display: "block", ...style }}
      data-ad-client={ADSENSE_CLIENT}
      data-ad-slot={slot}
      data-ad-format={format}
      data-full-width-responsive={responsive ? "true" : "false"}
    />
  );
}
