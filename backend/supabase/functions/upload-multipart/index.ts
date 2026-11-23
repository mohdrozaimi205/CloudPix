// POST /functions/v1/upload-multipart
// action: "sign"     { mediaId, partNumbers: [1,2,...] }  → URL PUT untuk setiap bahagian
//         "list"     { mediaId }                         → bahagian yang sudah diterima (untuk resume)
//         "complete" { mediaId }                         → gabung bahagian & muktamadkan
//         "abort"    { mediaId }                         → batal & buang
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuid } from "../_shared/http.ts";
import { finalizeMedia, getOwnedMedia, publicMedia, storageStatsFor, warningLevel } from "../_shared/media.ts";
import { abortMultipart, completeMultipart, deleteObject, listParts, presignPart } from "../_shared/r2.ts";

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "upload-multipart", 6000, 3600);

  const body = await readJson(req);
  const action = String(body.action ?? "");
  const media = await getOwnedMedia(user.id, uuid(body.mediaId, "mediaId"));

  const { data: session } = await admin.from("upload_sessions").select("*").eq("media_id", media.id).maybeSingle();

  if (action === "abort") {
    if (session) await abortMultipart(session.r2_key, session.r2_upload_id);
    if (media.status !== "ready") {
      await deleteObject(media.r2_key).catch(() => {});
      await admin.from("media").delete().eq("id", media.id);
    }
    return json(req, { aborted: true });
  }

  if (media.status === "ready") {
    return json(req, { completed: true, media: publicMedia(media) });
  }
  if (!session) throw new HttpError(404, "NO_SESSION", "Tiada sesi multipart untuk fail ini.");
  if (new Date(session.expires_at) < new Date()) {
    throw new HttpError(410, "UPLOAD_EXPIRED", "Sesi upload sudah tamat. Mulakan semula.");
  }

  switch (action) {
    case "sign": {
      const nums = Array.isArray(body.partNumbers) ? body.partNumbers.map(Number) : [];
      if (nums.length === 0 || nums.length > 100) {
        throw new HttpError(400, "BAD_INPUT", "partNumbers mesti 1–100 nombor.");
      }
      const urls: Record<number, string> = {};
      for (const n of nums) {
        if (!Number.isInteger(n) || n < 1 || n > session.part_count) {
          throw new HttpError(400, "BAD_INPUT", `Nombor bahagian ${n} tidak sah.`);
        }
        urls[n] = await presignPart(session.r2_key, session.r2_upload_id, n);
      }
      return json(req, { partSize: session.part_size, partCount: session.part_count, urls, expiresInSeconds: 3600 });
    }

    case "list": {
      const parts = await listParts(session.r2_key, session.r2_upload_id);
      return json(req, {
        partSize: session.part_size,
        partCount: session.part_count,
        parts: parts.map((p) => ({ partNumber: p.partNumber, size: p.size })),
        uploadedBytes: parts.reduce((s, p) => s + p.size, 0),
      });
    }

    case "complete": {
      const parts = await listParts(session.r2_key, session.r2_upload_id);
      const missing: number[] = [];
      const have = new Set(parts.map((p) => p.partNumber));
      for (let i = 1; i <= session.part_count; i++) if (!have.has(i)) missing.push(i);
      if (missing.length) {
        throw new HttpError(409, "PARTS_MISSING", "Masih ada bahagian belum diupload.", {
          missing: missing.slice(0, 200),
        });
      }
      await completeMultipart(session.r2_key, session.r2_upload_id, parts);
      const done = await finalizeMedia(media);
      const stats = await storageStatsFor(user.id);
      return json(req, {
        completed: true,
        media: publicMedia(done),
        storage: { ...stats, warningLevel: warningLevel(stats.usedPercent) },
      });
    }

    default:
      throw new HttpError(400, "BAD_INPUT", "action mesti sign, list, complete atau abort.");
  }
}));
