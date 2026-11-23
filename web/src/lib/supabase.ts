import { createClient, type SupabaseClient } from "@supabase/supabase-js";

const SERVER_KEY = "cloudpix.server";
const REMEMBER_KEY = "cloudpix.remember";

export interface ServerConfig {
  url: string;
  anonKey: string;
}

/** Konfigurasi server: dari build (GitHub Variables) atau disimpan pengguna di skrin "Sambung server". */
export function getServerConfig(): ServerConfig | null {
  try {
    const saved = localStorage.getItem(SERVER_KEY);
    if (saved) {
      const v = JSON.parse(saved) as ServerConfig;
      if (v.url && v.anonKey) return v;
    }
  } catch {
    /* abaikan */
  }
  const url = (import.meta.env.VITE_SUPABASE_URL as string | undefined) ?? "";
  const anonKey = (import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined) ?? "";
  return url && anonKey ? { url: url.replace(/\/+$/, ""), anonKey } : null;
}

export function saveServerConfig(c: ServerConfig) {
  localStorage.setItem(SERVER_KEY, JSON.stringify({ url: c.url.trim().replace(/\/+$/, ""), anonKey: c.anonKey.trim() }));
}

export function clearServerConfig() {
  localStorage.removeItem(SERVER_KEY);
}

export function setRememberMe(v: boolean) {
  localStorage.setItem(REMEMBER_KEY, v ? "1" : "0");
}
export function rememberMe(): boolean {
  return localStorage.getItem(REMEMBER_KEY) !== "0";
}

// "Ingat saya": simpan sesi dalam localStorage (kekal) atau sessionStorage (hilang bila tab ditutup).
const authStorage = {
  getItem: (k: string) => localStorage.getItem(k) ?? sessionStorage.getItem(k),
  setItem: (k: string, v: string) => {
    if (rememberMe()) {
      localStorage.setItem(k, v);
      sessionStorage.removeItem(k);
    } else {
      sessionStorage.setItem(k, v);
      localStorage.removeItem(k);
    }
  },
  removeItem: (k: string) => {
    localStorage.removeItem(k);
    sessionStorage.removeItem(k);
  },
};

export const config = getServerConfig();

export const supabase: SupabaseClient = createClient(
  config?.url ?? "https://belum-dikonfigurasi.supabase.co",
  config?.anonKey ?? "belum-dikonfigurasi",
  {
    auth: {
      storage: authStorage,
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: true,
      flowType: "pkce",
    },
  },
);

/** URL asas app (untuk redirect e-mel). */
export function appBaseUrl(): string {
  return window.location.origin + window.location.pathname;
}
