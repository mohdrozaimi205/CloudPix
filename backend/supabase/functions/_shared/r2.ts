// Integrasi Cloudflare R2 (API serasi S3) menggunakan tandatangan AWS SigV4.
// Kunci rahsia R2 hanya dibaca dari Supabase Secrets (pelayan).
import { AwsClient } from "npm:aws4fetch@1.0.20";
import { HttpError } from "./http.ts";

const ACCOUNT_ID = Deno.env.get("R2_ACCOUNT_ID") ?? "";
export const BUCKET = Deno.env.get("R2_BUCKET") ?? "";
const ENDPOINT = `https://${ACCOUNT_ID}.r2.cloudflarestorage.com`;

const client = new AwsClient({
  accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID") ?? "",
  secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY") ?? "",
  service: "s3",
  region: "auto",
});

function objectUrl(key: string, query: Record<string, string> = {}): URL {
  if (!ACCOUNT_ID || !BUCKET) throw new HttpError(500, "R2_NOT_CONFIGURED", "Storan R2 belum dikonfigurasi.");
  const path = key.split("/").map(encodeURIComponent).join("/");
  const url = new URL(`${ENDPOINT}/${BUCKET}/${path}`);
  for (const [k, v] of Object.entries(query)) url.searchParams.set(k, v);
  return url;
}

/** URL bertandatangan dengan tempoh luput (maks 7 hari). */
export async function presign(
  method: "GET" | "PUT",
  key: string,
  expiresSeconds: number,
  query: Record<string, string> = {},
): Promise<string> {
  const url = objectUrl(key, query);
  url.searchParams.set("X-Amz-Expires", String(Math.min(Math.max(expiresSeconds, 60), 604800)));
  const signed = await client.sign(url.toString(), { method, aws: { signQuery: true } });
  return signed.url;
}

async function r2(method: string, key: string, query: Record<string, string> = {}, init: RequestInit = {}) {
  const res = await client.fetch(objectUrl(key, query).toString(), { method, ...init });
  return res;
}

function tag(xml: string, name: string): string | null {
  const m = xml.match(new RegExp(`<${name}>([\\s\\S]*?)</${name}>`));
  return m ? m[1] : null;
}

export async function headObject(key: string): Promise<{ size: number } | null> {
  const res = await r2("HEAD", key);
  if (res.status === 404) return null;
  if (!res.ok) throw new HttpError(502, "R2_ERROR", `R2 HEAD gagal (${res.status}).`);
  return { size: Number(res.headers.get("content-length") ?? "0") };
}

/** Baca beberapa bait pertama (untuk semak jenis fail sebenar). */
export async function readHead(key: string, bytes = 64): Promise<Uint8Array> {
  const res = await r2("GET", key, {}, { headers: { Range: `bytes=0-${bytes - 1}` } });
  if (!res.ok) throw new HttpError(502, "R2_ERROR", `R2 GET gagal (${res.status}).`);
  return new Uint8Array(await res.arrayBuffer());
}

export async function deleteObject(key: string): Promise<void> {
  const res = await r2("DELETE", key);
  if (!res.ok && res.status !== 404) throw new HttpError(502, "R2_ERROR", `R2 DELETE gagal (${res.status}).`);
}

// ---------------- Multipart ----------------

export async function createMultipart(key: string, contentType: string): Promise<string> {
  const res = await r2("POST", key, { uploads: "" }, { headers: { "Content-Type": contentType } });
  const body = await res.text();
  const id = tag(body, "UploadId");
  if (!res.ok || !id) throw new HttpError(502, "R2_ERROR", `Gagal mula multipart (${res.status}).`);
  return id;
}

export function presignPart(key: string, uploadId: string, partNumber: number, expiresSeconds = 3600) {
  return presign("PUT", key, expiresSeconds, { partNumber: String(partNumber), uploadId });
}

export interface UploadedPart {
  partNumber: number;
  etag: string;
  size: number;
}

/** Senarai bahagian yang sudah diterima R2 (asas untuk resume). */
export async function listParts(key: string, uploadId: string): Promise<UploadedPart[]> {
  const parts: UploadedPart[] = [];
  let marker = "0";
  for (let i = 0; i < 20; i++) {
    const res = await r2("GET", key, { uploadId, "max-parts": "1000", "part-number-marker": marker });
    const body = await res.text();
    if (res.status === 404) throw new HttpError(410, "UPLOAD_EXPIRED", "Sesi upload sudah tamat. Mulakan semula.");
    if (!res.ok) throw new HttpError(502, "R2_ERROR", `Gagal senarai bahagian (${res.status}).`);
    for (const m of body.matchAll(/<Part>([\s\S]*?)<\/Part>/g)) {
      parts.push({
        partNumber: Number(tag(m[1], "PartNumber")),
        etag: (tag(m[1], "ETag") ?? "").replaceAll("&quot;", '"'),
        size: Number(tag(m[1], "Size") ?? "0"),
      });
    }
    if (tag(body, "IsTruncated") !== "true") break;
    marker = tag(body, "NextPartNumberMarker") ?? marker;
  }
  return parts.sort((a, b) => a.partNumber - b.partNumber);
}

export async function completeMultipart(key: string, uploadId: string, parts: UploadedPart[]): Promise<void> {
  const xml = "<CompleteMultipartUpload>" +
    parts.map((p) => `<Part><PartNumber>${p.partNumber}</PartNumber><ETag>${p.etag}</ETag></Part>`).join("") +
    "</CompleteMultipartUpload>";
  const res = await r2("POST", key, { uploadId }, { body: xml, headers: { "Content-Type": "application/xml" } });
  const body = await res.text();
  // S3 boleh pulang 200 dengan <Error> dalam badan
  if (!res.ok || body.includes("<Error>")) {
    throw new HttpError(502, "R2_ERROR", `Gagal lengkapkan multipart: ${tag(body, "Message") ?? res.status}`);
  }
}

export async function abortMultipart(key: string, uploadId: string): Promise<void> {
  const res = await r2("DELETE", key, { uploadId });
  if (!res.ok && res.status !== 404) console.error("abort multipart failed", res.status);
}
