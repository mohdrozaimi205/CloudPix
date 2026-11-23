// POST /functions/v1/purge-trash   (dipanggil oleh pg_cron setiap hari)
// Dilindungi header x-cron-secret. Tugas:
//   1. Padam kekal media dalam Recently Deleted > 30 hari
//   2. Buang upload tergantung (pending/failed > 7 hari)
//   3. Bersihkan kaunter rate limit lama
import { admin } from "../_shared/auth.ts";
import { handler, HttpError, json } from "../_shared/http.ts";
import { purgeMedia } from "../_shared/media.ts";

const SECRET = Deno.env.get("CRON_SECRET") ?? "";

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

Deno.serve(handler(async (req) => {
  const given = req.headers.get("x-cron-secret") ?? "";
  if (SECRET.length < 24 || !safeEqual(given, SECRET)) throw new HttpError(401, "UNAUTHORIZED", "Tidak dibenarkan.");

  const day = 24 * 3600 * 1000;
  const trashCutoff = new Date(Date.now() - 30 * day).toISOString();
  // sama dengan tempoh sesi multipart (7 hari) supaya upload besar yang di-resume tidak terpadam
  const pendingCutoff = new Date(Date.now() - 7 * day).toISOString();
  let trashed = 0;
  let stale = 0;

  for (let round = 0; round < 20; round++) {
    const { data } = await admin
      .from("media")
      .select("id, user_id, r2_key, thumb_key")
      .lt("deleted_at", trashCutoff)
      .limit(500);
    if (!data?.length) break;
    trashed += await purgeMedia(data);
  }

  for (let round = 0; round < 20; round++) {
    const { data } = await admin
      .from("media")
      .select("id, user_id, r2_key, thumb_key")
      .neq("status", "ready")
      .lt("created_at", pendingCutoff)
      .limit(500);
    if (!data?.length) break;
    stale += await purgeMedia(data);
  }

  await admin.from("rate_limits").delete().lt("window_start", new Date(Date.now() - day).toISOString());

  return json(req, { purgedTrash: trashed, purgedStaleUploads: stale });
}));
