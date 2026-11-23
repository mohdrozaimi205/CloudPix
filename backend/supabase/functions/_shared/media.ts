// Logik media dikongsi: kunci R2, muktamadkan upload, padam kekal.
import { admin } from "./auth.ts";
import { HttpError } from "./http.ts";
import { abortMultipart, deleteObject, headObject, readHead } from "./r2.ts";
import { extOfKey, magicMatches, THUMB_MAX, thumbMagicOk } from "./validate.ts";

export const originalKey = (userId: string, mediaId: string, ext: string) => `u/${userId}/o/${mediaId}.${ext}`;
export const thumbKeyFor = (userId: string, mediaId: string) => `u/${userId}/t/${mediaId}.jpg`;

export interface MediaRow {
  id: string;
  user_id: string;
  album_id: string | null;
  kind: "image" | "video";
  status: "pending" | "ready" | "failed";
  filename: string;
  mime_type: string;
  size_bytes: number;
  sha256: string | null;
  r2_key: string;
  thumb_key: string | null;
  width: number | null;
  height: number | null;
  duration_ms: number | null;
  is_favourite: boolean;
  taken_at: string | null;
  last_accessed_at: string | null;
  deleted_at: string | null;
  source: string;
  created_at: string;
  updated_at: string;
}

export const PUBLIC_COLUMNS =
  "id, album_id, kind, status, filename, mime_type, size_bytes, sha256, width, height, duration_ms, is_favourite, taken_at, last_accessed_at, deleted_at, source, created_at, updated_at, thumb_key";

/** Bentuk media yang selamat dihantar ke klien (tanpa kunci R2). */
export function publicMedia(m: MediaRow): Record<string, unknown> {
  const src = m as unknown as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const c of PUBLIC_COLUMNS.split(",").map((s) => s.trim())) out[c] = src[c];
  out.has_thumb = Boolean(m.thumb_key);
  delete out.thumb_key;
  return out;
}

/** Ambil media milik pengguna, atau 404. Semakan pemilikan dibuat di sini. */
export async function getOwnedMedia(userId: string, mediaId: string): Promise<MediaRow> {
  const { data, error } = await admin.from("media").select("*").eq("id", mediaId).eq("user_id", userId).maybeSingle();
  if (error) throw new HttpError(500, "DB_ERROR", error.message);
  if (!data) throw new HttpError(404, "NOT_FOUND", "Fail tidak dijumpai.");
  return data as MediaRow;
}

async function markFailed(m: MediaRow, reason: string) {
  await deleteObject(m.r2_key).catch(() => {});
  await deleteObject(thumbKeyFor(m.user_id, m.id)).catch(() => {});
  await admin.from("media").update({ status: "failed" }).eq("id", m.id);
  console.warn(`media ${m.id} failed: ${reason}`);
}

/**
 * Sahkan fail benar-benar ada dalam R2, saiz sama dengan yang diisytihar,
 * dan kandungan sepadan dengan jenisnya. Hanya selepas itu status jadi 'ready'
 * dan storage_used dikemas kini (oleh trigger database).
 */
export async function finalizeMedia(m: MediaRow): Promise<MediaRow> {
  if (m.status === "ready") return m;
  if (m.status === "failed") throw new HttpError(409, "UPLOAD_FAILED", "Upload ini telah gagal. Sila cuba semula.");

  const head = await headObject(m.r2_key);
  if (!head) throw new HttpError(409, "NOT_UPLOADED", "Fail belum diterima storan. Upload belum selesai.");

  if (head.size !== Number(m.size_bytes)) {
    await markFailed(m, `size mismatch ${head.size} vs ${m.size_bytes}`);
    throw new HttpError(422, "SIZE_MISMATCH", "Saiz fail yang diterima tidak sama dengan saiz yang diisytihar.");
  }

  const magic = await readHead(m.r2_key, 64);
  if (!magicMatches(extOfKey(m.r2_key), magic)) {
    await markFailed(m, "magic mismatch");
    throw new HttpError(415, "CONTENT_MISMATCH", "Kandungan fail tidak sepadan dengan jenis fail.");
  }

  // Thumbnail adalah pilihan; disimpan jika sah.
  let thumbKey: string | null = null;
  const tk = thumbKeyFor(m.user_id, m.id);
  const th = await headObject(tk);
  if (th && th.size > 0 && th.size <= THUMB_MAX && thumbMagicOk(await readHead(tk, 16))) {
    thumbKey = tk;
  } else if (th) {
    await deleteObject(tk).catch(() => {});
  }

  const { data, error } = await admin
    .from("media")
    .update({ status: "ready", thumb_key: thumbKey })
    .eq("id", m.id)
    .eq("status", "pending")
    .select("*")
    .single();
  if (error) throw new HttpError(500, "DB_ERROR", error.message);

  await admin.from("upload_sessions").delete().eq("media_id", m.id);
  return data as MediaRow;
}

/** Padam kekal: objek R2 + baris database (trigger kurangkan storage_used). */
export async function purgeMedia(rows: Pick<MediaRow, "id" | "user_id" | "r2_key" | "thumb_key">[]): Promise<number> {
  if (rows.length === 0) return 0;
  const ids = rows.map((r) => r.id);

  const { data: sessions } = await admin.from("upload_sessions").select("r2_key, r2_upload_id").in("media_id", ids);
  for (const s of sessions ?? []) await abortMultipart(s.r2_key, s.r2_upload_id);

  const keys = rows.flatMap((r) => [r.r2_key, r.thumb_key ?? thumbKeyFor(r.user_id, r.id)]);
  // padam selari, 16 serentak
  for (let i = 0; i < keys.length; i += 16) {
    await Promise.all(keys.slice(i, i + 16).map((k) => deleteObject(k)));
  }

  // kosongkan cover album yang merujuk media ini
  await admin.from("albums").update({ cover_media_id: null }).in("cover_media_id", ids);
  const { error } = await admin.from("media").delete().in("id", ids);
  if (error) throw new HttpError(500, "DB_ERROR", error.message);
  return ids.length;
}

export async function storageStatsFor(userId: string) {
  const { data } = await admin.from("profiles").select("storage_quota_bytes, storage_used_bytes").eq("id", userId)
    .single();
  const quota = Number(data?.storage_quota_bytes ?? 0);
  const used = Number(data?.storage_used_bytes ?? 0);
  return {
    quotaBytes: quota,
    usedBytes: used,
    availableBytes: Math.max(quota - used, 0),
    usedPercent: quota ? Math.round((used / quota) * 10000) / 100 : 0,
  };
}

/** Tahap amaran 80 / 90 / 95 %. */
export function warningLevel(percent: number): 0 | 80 | 90 | 95 {
  if (percent >= 95) return 95;
  if (percent >= 90) return 90;
  if (percent >= 80) return 80;
  return 0;
}
