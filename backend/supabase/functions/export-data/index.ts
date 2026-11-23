// POST /functions/v1/export-data  { page?: 0, includeDownloadUrls?: true }
// Eksport semua data akaun (JSON). Media dipecah 2000 setiap halaman.
// URL muat turun asal sah 24 jam.
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson } from "../_shared/http.ts";
import { presign } from "../_shared/r2.ts";

const PAGE = 2000;

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "export-data", 200, 3600);

  const body = await readJson(req);
  const page = Math.max(0, Math.floor(Number(body.page ?? 0)));
  const withUrls = body.includeDownloadUrls !== false;

  const { data: media, error, count } = await admin
    .from("media")
    .select(
      "id, album_id, kind, filename, mime_type, size_bytes, sha256, width, height, duration_ms, is_favourite, taken_at, deleted_at, created_at, r2_key",
      { count: "exact" },
    )
    .eq("user_id", user.id)
    .eq("status", "ready")
    .order("created_at", { ascending: true })
    .range(page * PAGE, page * PAGE + PAGE - 1);
  if (error) throw new HttpError(500, "DB_ERROR", error.message);

  const items = [];
  for (const m of media ?? []) {
    const { r2_key, ...rest } = m;
    items.push({
      ...rest,
      downloadUrl: withUrls
        ? await presign("GET", r2_key, 86400, {
          "response-content-disposition": `attachment; filename*=UTF-8''${encodeURIComponent(m.filename)}`,
        })
        : undefined,
    });
  }

  const result: Record<string, unknown> = {
    exportedAt: new Date().toISOString(),
    page,
    pageSize: PAGE,
    totalMedia: count ?? 0,
    hasMore: (page + 1) * PAGE < (count ?? 0),
    media: items,
  };

  if (page === 0) {
    const [profile, albums, backups, shares] = await Promise.all([
      admin.from("profiles").select("display_name, storage_quota_bytes, storage_used_bytes, created_at").eq(
        "id",
        user.id,
      ).single(),
      admin.from("albums").select("id, name, cover_media_id, created_at").eq("user_id", user.id),
      admin.from("backup_history").select("*").eq("user_id", user.id).order("started_at", { ascending: false }).limit(
        1000,
      ),
      admin.from("share_links").select("id, media_id, expires_at, revoked, view_count, created_at").eq(
        "user_id",
        user.id,
      ),
    ]);
    result.account = { email: user.email, createdAt: user.created_at, ...profile.data };
    result.albums = albums.data ?? [];
    result.backupHistory = backups.data ?? [];
    result.shareLinks = shares.data ?? [];
  }

  return json(req, result);
}));
