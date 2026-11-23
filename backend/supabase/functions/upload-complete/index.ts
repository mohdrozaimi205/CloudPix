// POST /functions/v1/upload-complete  { mediaId }
// Untuk upload mod "single": sahkan fail dalam R2 & tandakan siap.
import { rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuid } from "../_shared/http.ts";
import { finalizeMedia, getOwnedMedia, publicMedia, storageStatsFor, warningLevel } from "../_shared/media.ts";

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "upload-complete", 1200, 3600);

  const body = await readJson(req);
  const media = await getOwnedMedia(user.id, uuid(body.mediaId, "mediaId"));
  if (media.status === "pending" && media.size_bytes > 64 * 1024 * 1024) {
    throw new HttpError(400, "USE_MULTIPART", "Fail ini guna multipart — panggil upload-multipart action=complete.");
  }
  const done = await finalizeMedia(media);
  const stats = await storageStatsFor(user.id);

  return json(req, {
    completed: true,
    media: publicMedia(done),
    storage: { ...stats, warningLevel: warningLevel(stats.usedPercent) },
  });
}));
