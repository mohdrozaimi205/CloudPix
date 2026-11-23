// POST /functions/v1/media-url
// { ids: [...], variant: "thumb" | "original" | "download" }
// Pulangkan URL bertandatangan (luput) untuk fail milik pengguna sahaja.
// URL "original" menyokong Range → video boleh di-stream terus.
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuidList } from "../_shared/http.ts";
import { presign } from "../_shared/r2.ts";

const TTL = { thumb: 6 * 3600, original: 3600, download: 3600 } as const;

function contentDisposition(filename: string): string {
  const ascii = filename.replace(/[^\x20-\x7e]/g, "_").replace(/["\\]/g, "_");
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(filename)}`;
}

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "media-url", 3000, 300);

  const body = await readJson(req);
  const variant = String(body.variant ?? "thumb") as keyof typeof TTL;
  if (!(variant in TTL)) throw new HttpError(400, "BAD_INPUT", "variant mesti thumb, original atau download.");
  const ids = uuidList(body.ids, "ids", variant === "thumb" ? 200 : 50);

  const { data, error } = await admin
    .from("media")
    .select("id, r2_key, thumb_key, filename, mime_type")
    .eq("user_id", user.id) // semakan pemilikan
    .eq("status", "ready")
    .in("id", ids);
  if (error) throw new HttpError(500, "DB_ERROR", error.message);

  const ttl = TTL[variant];
  const expiresAt = new Date(Date.now() + ttl * 1000).toISOString();
  const urls: Record<string, { url: string | null; expiresAt: string }> = {};

  for (const m of data ?? []) {
    let url: string | null = null;
    if (variant === "thumb") {
      url = m.thumb_key
        ? await presign("GET", m.thumb_key, ttl, { "response-cache-control": "private, max-age=21600" })
        : null;
    } else {
      const q: Record<string, string> = { "response-content-type": m.mime_type };
      if (variant === "download") q["response-content-disposition"] = contentDisposition(m.filename);
      url = await presign("GET", m.r2_key, ttl, q);
    }
    urls[m.id] = { url, expiresAt };
  }

  if (variant !== "thumb" && data?.length) {
    await admin.from("media").update({ last_accessed_at: new Date().toISOString() })
      .eq("user_id", user.id).in("id", data.map((m) => m.id));
  }

  return json(req, { urls, missing: ids.filter((id) => !urls[id]) });
}));
