// GET /functions/v1/share-public?token=xxxx   (tanpa log masuk)
// Pulangkan metadata + URL luput 1 jam untuk fail yang dikongsi.
import { admin, rateLimit } from "../_shared/auth.ts";
import { clientIp, handler, HttpError, json } from "../_shared/http.ts";
import { presign } from "../_shared/r2.ts";

Deno.serve(handler(async (req) => {
  if (req.method !== "GET") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Guna GET.");
  await rateLimit(`ip:${clientIp(req)}`, "share-public", 120, 300);

  const token = new URL(req.url).searchParams.get("token") ?? "";
  if (!/^[0-9a-f]{48}$/.test(token)) throw new HttpError(404, "NOT_FOUND", "Pautan tidak sah.");

  const { data: link } = await admin
    .from("share_links")
    .select("id, media_id, expires_at, revoked, view_count")
    .eq("token", token)
    .maybeSingle();
  if (!link || link.revoked || new Date(link.expires_at) < new Date()) {
    throw new HttpError(404, "NOT_FOUND", "Pautan tidak sah atau sudah luput.");
  }

  const { data: m } = await admin
    .from("media")
    .select("filename, kind, mime_type, size_bytes, width, height, duration_ms, r2_key, thumb_key, status, deleted_at")
    .eq("id", link.media_id)
    .maybeSingle();
  if (!m || m.status !== "ready" || m.deleted_at) throw new HttpError(404, "NOT_FOUND", "Fail sudah tiada.");

  await admin.from("share_links").update({ view_count: link.view_count + 1 }).eq("id", link.id);

  const ttl = Math.min(3600, Math.max(60, Math.floor((new Date(link.expires_at).getTime() - Date.now()) / 1000)));
  const url = await presign("GET", m.r2_key, ttl, { "response-content-type": m.mime_type });
  const thumbUrl = m.thumb_key ? await presign("GET", m.thumb_key, ttl) : null;

  return json(req, {
    filename: m.filename,
    kind: m.kind,
    mimeType: m.mime_type,
    sizeBytes: m.size_bytes,
    width: m.width,
    height: m.height,
    durationMs: m.duration_ms,
    url,
    thumbUrl,
    linkExpiresAt: link.expires_at,
  });
}));
