"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** /app → /app/comparar */
export default function AppIndex() {
  const router = useRouter();
  useEffect(() => {
    router.replace("/app/comparar/");
  }, [router]);
  return null;
}
