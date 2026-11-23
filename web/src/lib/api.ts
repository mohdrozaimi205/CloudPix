import { config, supabase } from "./supabase";
import type { Media, StorageInfo } from "./types";

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: Record<string, unknown>) {
    super(message);
  }
}

/** Panggil Edge Function CloudPix dengan token pengguna. */
export async function callFn<T>(name: string, body: unknown): Promise<T> {
  if (!config) throw new ApiError(0, "NO_CONFIG", "Server belum disambung.");
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (!token) throw new ApiError(401, "UNAUTHENTICATED", "Sila log masuk.");
  let res: Response;
  try {
    res = await fetch(`${config.url}/functions/v1/${name}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, apikey: config.anonKey, "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "NETWORK", "Tiada sambungan internet.");
  }
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    const e = json?.error ?? {};
    throw new ApiError(res.status, e.code ?? `HTTP_${res.status}`, e.message ?? `Ralat pelayan (${res.status}).`, e);
  }
  return json as T;
}

export interface InitResponse {
  duplicate: boolean;
  existing?: { id: string; filename: string; created_at: string };
  mode?: "single" | "multipart";
  mediaId: string;
  uploadUrl?: string;
  thumbUploadUrl: string;
  contentType: string;
  partSize?: number;
  partCount?: number;
  storage: StorageInfo;
}

export const api = {
  uploadInit: (b: Record<string, unknown>) => callFn<InitResponse>("upload-init", b),
  uploadComplete: (mediaId: string) =>
    callFn<{ media: Media; storage: StorageInfo }>("upload-complete", { mediaId }),
  multipart: <T>(action: "sign" | "list" | "complete" | "abort", b: Record<string, unknown>) =>
    callFn<T>("upload-multipart", { action, ...b }),
  mediaUrl: (ids: string[], variant: "thumb" | "original" | "download") =>
    callFn<{ urls: Record<string, { url: string | null; expiresAt: string }>; missing: string[] }>("media-url", {
      ids,
      variant,
    }),
  mediaDelete: (ids: string[], action: "trash" | "restore" | "purge") =>
    callFn<{ affected: number }>("media-delete", { ids, action }),
  shareCreate: (mediaId: string, expiresInHours: number) =>
    callFn<{ id: string; token: string; url: string | null; expiresAt: string }>("share-create", {
      mediaId,
      expiresInHours,
    }),
  exportData: (page: number) => callFn<Record<string, unknown> & { hasMore: boolean; media: unknown[] }>("export-data", { page }),
};
