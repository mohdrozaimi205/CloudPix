// POST /functions/v1/upload-init
// Mula upload: semak jenis, saiz, kuota 1TB, duplikat; pulangkan URL upload bertandatangan.
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuid } from "../_shared/http.ts";
import { originalKey, storageStatsFor, thumbKeyFor, warningLevel } from "../_shared/media.ts";
import { createMultipart, presign } from "../_shared/r2.ts";
import {
  fileTypeFor,
  optDate,
  optInt,
  partSizeFor,
  sanitizeFilename,
  SINGLE_PUT_MAX,
  validSize,
} from "../_shared/validate.ts";

const URL_TTL = 3600; // 1 jam

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "upload-init", 1200, 3600);

  const body = await readJson(req);
  const filename = sanitizeFilename(body.filename);
  const size = validSize(body.size);
  const type = fileTypeFor(filename);
  const sha256 = typeof body.sha256 === "string" && /^[0-9a-f]{64}$/.test(body.sha256) ? body.sha256 : null;
  const albumId = body.albumId ? uuid(body.albumId, "albumId") : null;
  const source = ["web", "android", "backup"].includes(String(body.source)) ? String(body.source) : "web";

  // Pengesanan duplikat berdasarkan hash SHA-256
  if (sha256 && body.allowDuplicate !== true) {
    const { data: dup } = await admin
      .from("media")
      .select("id, filename, created_at")
      .eq("user_id", user.id)
      .eq("sha256", sha256)
      .eq("status", "ready")
      .is("deleted_at", null)
      .limit(1)
      .maybeSingle();
    if (dup) return json(req, { duplicate: true, existing: dup });
  }

  const mediaId = crypto.randomUUID();
  const r2Key = originalKey(user.id, mediaId, type.ext);

  const { data: reserve, error } = await admin.rpc("create_pending_media", {
    p_id: mediaId,
    p_user: user.id,
    p_kind: type.kind,
    p_filename: filename,
    p_mime: type.mime,
    p_size: size,
    p_sha256: sha256,
    p_r2_key: r2Key,
    p_album: albumId,
    p_width: optInt(body.width),
    p_height: optInt(body.height),
    p_duration_ms: optInt(body.durationMs, Number.MAX_SAFE_INTEGER),
    p_taken_at: optDate(body.takenAt),
    p_source: source,
  });
  if (error) throw new HttpError(500, "DB_ERROR", error.message);
  if (!reserve?.ok) {
    const stats = await storageStatsFor(user.id);
    throw new HttpError(507, "QUOTA_EXCEEDED", "Ruang storan tidak mencukupi untuk fail ini.", { storage: stats });
  }

  const thumbUploadUrl = await presign("PUT", thumbKeyFor(user.id, mediaId), URL_TTL);
  const stats = await storageStatsFor(user.id);
  const base = {
    duplicate: false,
    mediaId,
    contentType: type.mime,
    kind: type.kind,
    thumbUploadUrl,
    expiresInSeconds: URL_TTL,
    storage: { ...stats, warningLevel: warningLevel(stats.usedPercent) },
  };

  if (size <= SINGLE_PUT_MAX) {
    const uploadUrl = await presign("PUT", r2Key, URL_TTL);
    return json(req, { ...base, mode: "single", uploadUrl });
  }

  // Fail besar: multipart (boleh resume)
  let uploadId: string;
  try {
    uploadId = await createMultipart(r2Key, type.mime);
  } catch (e) {
    await admin.from("media").delete().eq("id", mediaId);
    throw e;
  }
  const partSize = partSizeFor(size);
  const partCount = Math.ceil(size / partSize);
  const { error: sErr } = await admin.from("upload_sessions").insert({
    user_id: user.id,
    media_id: mediaId,
    r2_key: r2Key,
    r2_upload_id: uploadId,
    part_size: partSize,
    part_count: partCount,
  });
  if (sErr) {
    await admin.from("media").delete().eq("id", mediaId);
    throw new HttpError(500, "DB_ERROR", sErr.message);
  }

  return json(req, { ...base, mode: "multipart", partSize, partCount });
}));
