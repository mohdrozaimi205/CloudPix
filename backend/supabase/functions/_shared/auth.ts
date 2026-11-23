// Klien admin (service role) — HANYA wujud di pelayan, tidak pernah dihantar ke APK/web.
import { createClient, type User } from "npm:@supabase/supabase-js@2";
import { HttpError } from "./http.ts";

export const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false, autoRefreshToken: false } },
);

/** Sahkan token pengguna dari header Authorization. */
export async function requireUser(req: Request): Promise<User> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "").trim();
  if (!token) throw new HttpError(401, "UNAUTHENTICATED", "Sila log masuk.");
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new HttpError(401, "UNAUTHENTICATED", "Sesi tamat. Sila log masuk semula.");
  return data.user;
}

/** Had kadar. key = user id atau "ip:x.x.x.x". */
export async function rateLimit(key: string, bucket: string, max: number, windowSeconds: number): Promise<void> {
  const { data, error } = await admin.rpc("check_rate_limit", {
    p_key: key,
    p_bucket: bucket,
    p_max: max,
    p_window_seconds: windowSeconds,
  });
  if (error) {
    console.error("rate limit error", error);
    return; // jangan sekat pengguna jika kaunter gagal
  }
  if (data === false) {
    throw new HttpError(429, "RATE_LIMITED", "Terlalu banyak permintaan. Cuba sebentar lagi.", {
      retryAfterSeconds: windowSeconds,
    });
  }
}
