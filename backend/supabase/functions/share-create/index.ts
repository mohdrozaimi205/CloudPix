// POST /functions/v1/share-create  { mediaId, expiresInHours (1–720, lalai 24) }
// Cipta pautan kongsi rawak (token 192-bit) yang luput. Batal: update share_links.revoked = true.
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuid } from "../_shared/http.ts";
import { getOwnedMedia } from "../_shared/media.ts";

const WEB_URL = (Deno.env.get("PUBLIC_WEB_URL") ?? "").replace(/\/+$/, "");
// Pelawat perlu tahu alamat server; web app guna HashRouter (sesuai GitHub Pages)
const SERVER_HOST = new URL(Deno.env.get("SUPABASE_URL") ?? "https://x.supabase.co").host;

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "share-create", 100, 3600);

  const body = await readJson(req);
  const media = await getOwnedMedia(user.id, uuid(body.mediaId, "mediaId"));
  if (media.status !== "ready" || media.deleted_at) {
    throw new HttpError(409, "NOT_SHAREABLE", "Fail ini tidak boleh dikongsi.");
  }
  const hours = Math.min(Math.max(Math.round(Number(body.expiresInHours ?? 24)), 1), 720);
  const expiresAt = new Date(Date.now() + hours * 3600 * 1000).toISOString();

  const { data, error } = await admin
    .from("share_links")
    .insert({ user_id: user.id, media_id: media.id, expires_at: expiresAt })
    .select("id, token, expires_at")
    .single();
  if (error) throw new HttpError(500, "DB_ERROR", error.message);

  return json(req, {
    id: data.id,
    token: data.token,
    expiresAt: data.expires_at,
    url: WEB_URL ? `${WEB_URL}/#/s/${data.token}?s=${SERVER_HOST}` : null,
  });
}));
