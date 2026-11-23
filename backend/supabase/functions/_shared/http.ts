// Pembantu HTTP: CORS, ralat, JSON, wrapper handler.

export class HttpError extends Error {
  constructor(public status: number, public code: string, message: string, public extra?: Record<string, unknown>) {
    super(message);
  }
}

const allowed = (Deno.env.get("ALLOWED_ORIGINS") ?? "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get("Origin");
  let allowOrigin = "null";
  if (allowed.length === 0) allowOrigin = "*";
  else if (origin && allowed.includes(origin)) allowOrigin = origin;
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin",
  };
}

export function json(req: Request, body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders(req),
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

export function handler(fn: (req: Request) => Promise<Response>) {
  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders(req) });
    try {
      return await fn(req);
    } catch (e) {
      if (e instanceof HttpError) {
        return json(req, { error: { code: e.code, message: e.message, ...e.extra } }, e.status);
      }
      console.error("Unhandled error:", e);
      return json(req, { error: { code: "INTERNAL", message: "Ralat dalaman pelayan." } }, 500);
    }
  };
}

export async function readJson<T = Record<string, unknown>>(req: Request, maxBytes = 64 * 1024): Promise<T> {
  if (req.method !== "POST") throw new HttpError(405, "METHOD_NOT_ALLOWED", "Guna POST.");
  const text = await req.text();
  if (text.length > maxBytes) throw new HttpError(413, "BODY_TOO_LARGE", "Permintaan terlalu besar.");
  try {
    const v = JSON.parse(text || "{}");
    if (typeof v !== "object" || v === null || Array.isArray(v)) throw new Error();
    return v as T;
  } catch {
    throw new HttpError(400, "BAD_JSON", "Badan permintaan mesti JSON objek.");
  }
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function uuid(v: unknown, field: string): string {
  if (typeof v !== "string" || !UUID_RE.test(v)) throw new HttpError(400, "BAD_INPUT", `${field} tidak sah.`);
  return v.toLowerCase();
}

export function uuidList(v: unknown, field: string, max: number): string[] {
  if (!Array.isArray(v) || v.length === 0) throw new HttpError(400, "BAD_INPUT", `${field} mesti senarai.`);
  if (v.length > max) throw new HttpError(400, "BAD_INPUT", `${field} maksimum ${max} item.`);
  return [...new Set(v.map((x) => uuid(x, field)))];
}

export function clientIp(req: Request): string {
  return (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "unknown";
}
