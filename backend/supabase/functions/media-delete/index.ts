// POST /functions/v1/media-delete
// { ids: [...], action: "trash" | "restore" | "purge" }
//   trash   → pindah ke Recently Deleted (boleh pulih 30 hari)
//   restore → pulihkan dari Recently Deleted
//   purge   → padam kekal dari R2 & database (storan dibebaskan serta-merta)
import { admin, rateLimit, requireUser } from "../_shared/auth.ts";
import { handler, HttpError, json, readJson, uuidList } from "../_shared/http.ts";
import { purgeMedia, storageStatsFor } from "../_shared/media.ts";

Deno.serve(handler(async (req) => {
  const user = await requireUser(req);
  await rateLimit(user.id, "media-delete", 300, 3600);

  const body = await readJson(req);
  const action = String(body.action ?? "trash");
  const ids = uuidList(body.ids, "ids", 500);

  if (action === "trash" || action === "restore") {
    const { data, error } = await admin
      .from("media")
      .update({ deleted_at: action === "trash" ? new Date().toISOString() : null })
      .eq("user_id", user.id)
      .in("id", ids)
      .select("id");
    if (error) throw new HttpError(500, "DB_ERROR", error.message);
    return json(req, { action, affected: data?.length ?? 0 });
  }

  if (action === "purge") {
    const { data, error } = await admin
      .from("media")
      .select("id, user_id, r2_key, thumb_key")
      .eq("user_id", user.id)
      .in("id", ids);
    if (error) throw new HttpError(500, "DB_ERROR", error.message);
    const n = await purgeMedia(data ?? []);
    return json(req, { action, affected: n, storage: await storageStatsFor(user.id) });
  }

  throw new HttpError(400, "BAD_INPUT", "action mesti trash, restore atau purge.");
}));
