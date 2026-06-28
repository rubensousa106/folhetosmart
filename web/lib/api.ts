/**
 * Cliente HTTP do backend FolhetoSmart. Fluxo JWT igual ao da app Android:
 * guarda access + refresh token, envia "Authorization: Bearer" e, em 401,
 * tenta renovar uma vez via /api/v1/auth/refresh antes de desistir.
 */
import { SITE } from "./site";

const ACCESS_KEY = "folheto_token";
const REFRESH_KEY = "folheto_refresh";

export type AuthResponse = {
  token: string;
  refresh_token: string;
  email: string;
  role: string;
  must_change_password?: boolean;
};

export type ShoppingItem = {
  id: string;
  produto: string;
  supermercado: string;
  preco: number;
  quantity: number;
  created_at?: string;
  updated_at?: string;
};

/** Oferta vinda do feed /api/v1/products/all (snake_case do produtor). */
export type FlyerOffering = {
  produto: string;
  preco: number;
  supermercado: string;
  validade: string;
  original?: string;
  marca?: string;
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

// --- armazenamento de tokens (apenas no browser) ---------------------------

export const tokenStore = {
  get access() {
    return typeof window === "undefined" ? null : localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return typeof window === "undefined" ? null : localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

// --- núcleo de pedidos ------------------------------------------------------

async function rawRequest(path: string, init: RequestInit, withAuth: boolean) {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (withAuth && tokenStore.access) {
    headers.set("Authorization", `Bearer ${tokenStore.access}`);
  }
  return fetch(`${SITE.apiUrl}${path}`, { ...init, headers });
}

async function refreshTokens(): Promise<boolean> {
  const refresh = tokenStore.refresh;
  if (!refresh) return false;
  const res = await rawRequest(
    "/api/v1/auth/refresh",
    { method: "POST", body: JSON.stringify({ refresh_token: refresh }) },
    false,
  );
  if (!res.ok) return false;
  const data = (await res.json()) as AuthResponse;
  tokenStore.set(data.token, data.refresh_token);
  return true;
}

async function parseError(res: Response): Promise<never> {
  let message = `Erro ${res.status}`;
  try {
    const body = await res.json();
    if (body?.message) message = body.message;
  } catch {
    /* corpo não-JSON */
  }
  throw new ApiError(res.status, message);
}

/** Pedido autenticado com renovação automática do token em 401. */
export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let res = await rawRequest(path, init, true);
  if (res.status === 401 && tokenStore.refresh) {
    const renewed = await refreshTokens();
    if (renewed) {
      res = await rawRequest(path, init, true);
    }
  }
  if (!res.ok) return parseError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

// --- endpoints de autenticação ---------------------------------------------

export async function login(email: string, password: string) {
  const res = await rawRequest(
    "/api/v1/auth/login",
    { method: "POST", body: JSON.stringify({ email, password }) },
    false,
  );
  if (!res.ok) return parseError(res);
  const data = (await res.json()) as AuthResponse;
  tokenStore.set(data.token, data.refresh_token);
  return data;
}

export async function register(email: string, password: string) {
  const res = await rawRequest(
    "/api/v1/auth/register",
    { method: "POST", body: JSON.stringify({ email, password }) },
    false,
  );
  if (!res.ok) return parseError(res);
  const data = (await res.json()) as AuthResponse;
  tokenStore.set(data.token, data.refresh_token);
  return data;
}

export async function forgotPassword(email: string) {
  const res = await rawRequest(
    "/api/v1/auth/forgot-password",
    { method: "POST", body: JSON.stringify({ email }) },
    false,
  );
  if (!res.ok) return parseError(res);
  return res.json();
}

// --- feed de produtos -------------------------------------------------------

/** True se a validade ("… a DD/MM/AAAA") já terminou. */
export function isExpired(validade?: string): boolean {
  const fim = validade?.split(" a ").pop()?.trim();
  if (!fim) return false;
  const m = fim.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!m) return false;
  const end = new Date(Number(m[3]), Number(m[2]) - 1, Number(m[1]));
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return end < today;
}

/** Descarrega um feed do R2 (link assinado, origem externa). */
async function downloadFeed(url: string): Promise<FlyerOffering[]> {
  const res = await fetch(url);
  if (!res.ok) throw new ApiError(res.status, "Feed indisponível");
  return (await res.json()) as FlyerOffering[];
}

/**
 * Ofertas válidas de todos os supermercados. Espelha a app: lê os feeds ativos
 * (GET /products/feeds → links assinados R2) e funde-os; recorre a /products/all
 * (302 → R2) se os feeds falharem. Filtra as validades já expiradas.
 *
 * Nota: os ficheiros de feed vivem no R2 (origem externa). Para o browser os
 * poder ler, o bucket R2 precisa de uma regra CORS que permita o domínio do site.
 */
export async function fetchOfferings(): Promise<FlyerOffering[]> {
  let merged: FlyerOffering[] = [];
  try {
    const feeds = await apiFetch<string[]>("/api/v1/products/feeds");
    if (feeds.length > 0) {
      const lists = await Promise.all(
        feeds.map((url) => downloadFeed(url).catch(() => [] as FlyerOffering[])),
      );
      merged = lists.flat();
    }
  } catch {
    /* sem /feeds — tenta o endpoint único abaixo */
  }
  if (merged.length === 0) {
    merged = await apiFetch<FlyerOffering[]>("/api/v1/products/all");
  }
  return merged.filter((o) => !isExpired(o.validade));
}

// --- lista de compras (sincronizada com a conta) ---------------------------

export const shopping = {
  list: () => apiFetch<ShoppingItem[]>("/api/v1/shopping"),
  add: (produto: string, supermercado: string, preco: number, quantity = 1) =>
    apiFetch<ShoppingItem>("/api/v1/shopping", {
      method: "POST",
      body: JSON.stringify({ produto, supermercado, preco, quantity }),
    }),
  setQuantity: (id: string, quantity: number) =>
    apiFetch<ShoppingItem>(`/api/v1/shopping/${id}/quantity`, {
      method: "PATCH",
      body: JSON.stringify({ quantity }),
    }),
  remove: (id: string) =>
    apiFetch<void>(`/api/v1/shopping/${id}`, { method: "DELETE" }),
};
