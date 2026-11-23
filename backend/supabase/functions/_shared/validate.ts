// Validasi jenis & saiz fail.
import { HttpError } from "./http.ts";

export type Kind = "image" | "video";

interface FileType {
  ext: string;
  mime: string;
  kind: Kind;
}

const TYPES: Record<string, FileType> = {
  jpg: { ext: "jpg", mime: "image/jpeg", kind: "image" },
  jpeg: { ext: "jpg", mime: "image/jpeg", kind: "image" },
  png: { ext: "png", mime: "image/png", kind: "image" },
  webp: { ext: "webp", mime: "image/webp", kind: "image" },
  heic: { ext: "heic", mime: "image/heic", kind: "image" },
  heif: { ext: "heic", mime: "image/heic", kind: "image" },
  gif: { ext: "gif", mime: "image/gif", kind: "image" },
  mp4: { ext: "mp4", mime: "video/mp4", kind: "video" },
  m4v: { ext: "mp4", mime: "video/mp4", kind: "video" },
  mov: { ext: "mov", mime: "video/quicktime", kind: "video" },
  mkv: { ext: "mkv", mime: "video/x-matroska", kind: "video" },
  webm: { ext: "webm", mime: "video/webm", kind: "video" },
};

export const MB = 1024 * 1024;
export const GB = 1024 * MB;

/** Saiz maksimum satu fail (lalai 50 GB). */
export const MAX_FILE_BYTES = Number(Deno.env.get("MAX_FILE_BYTES") ?? 50 * GB);
/** Di bawah had ini guna satu PUT; di atas guna multipart. */
export const SINGLE_PUT_MAX = 64 * MB;
export const THUMB_MAX = 2 * MB;

export function fileTypeFor(filename: string): FileType {
  const ext = filename.split(".").pop()?.toLowerCase() ?? "";
  const t = TYPES[ext];
  if (!t) {
    throw new HttpError(
      415,
      "UNSUPPORTED_TYPE",
      "Jenis fail tidak disokong. Dibenarkan: JPG, PNG, WEBP, HEIC, GIF, MP4, MOV, MKV, WEBM.",
    );
  }
  return t;
}

export function sanitizeFilename(v: unknown): string {
  if (typeof v !== "string") throw new HttpError(400, "BAD_INPUT", "Nama fail diperlukan.");
  // buang laluan folder & aksara kawalan
  // deno-lint-ignore no-control-regex
  const name = v.split(/[\\/]/).pop()!.replace(/[\u0000-\u001f\u007f<>:"|?*]/g, "_").trim();
  if (!name || name === "." || name === "..") throw new HttpError(400, "BAD_INPUT", "Nama fail tidak sah.");
  return name.slice(0, 255);
}

export function validSize(v: unknown): number {
  const n = Number(v);
  if (!Number.isSafeInteger(n) || n <= 0) throw new HttpError(400, "BAD_INPUT", "Saiz fail tidak sah.");
  if (n > MAX_FILE_BYTES) {
    throw new HttpError(413, "FILE_TOO_LARGE", `Fail melebihi had ${Math.round(MAX_FILE_BYTES / GB)} GB.`);
  }
  return n;
}

export function optInt(v: unknown, max = 2_147_483_647): number | null {
  if (v === undefined || v === null || v === "") return null;
  const n = Number(v);
  if (!Number.isSafeInteger(n) || n < 0 || n > max) return null;
  return n;
}

export function optDate(v: unknown): string | null {
  if (typeof v !== "string") return null;
  const d = new Date(v);
  return isNaN(d.getTime()) ? null : d.toISOString();
}

/** Saiz bahagian multipart: min 16 MB, dan maksimum 10,000 bahagian. */
export function partSizeFor(size: number): number {
  const min = 16 * MB;
  const needed = Math.ceil(size / 10000);
  return Math.max(min, Math.ceil(needed / MB) * MB);
}

const ascii = (b: Uint8Array, start: number, len: number) => String.fromCharCode(...b.slice(start, start + len));

/** Semak "magic bytes" — pastikan kandungan fail sepadan dengan sambungannya. */
export function magicMatches(ext: string, b: Uint8Array): boolean {
  switch (ext) {
    case "jpg":
      return b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff;
    case "png":
      return b[0] === 0x89 && ascii(b, 1, 3) === "PNG";
    case "gif":
      return ascii(b, 0, 4) === "GIF8";
    case "webp":
      return ascii(b, 0, 4) === "RIFF" && ascii(b, 8, 4) === "WEBP";
    case "heic":
      return ascii(b, 4, 4) === "ftyp" &&
        ["heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif"].includes(ascii(b, 8, 4));
    case "mp4":
      return ascii(b, 4, 4) === "ftyp";
    case "mov":
      return ["ftyp", "moov", "wide", "mdat", "free", "skip", "pnot"].includes(ascii(b, 4, 4));
    case "mkv":
    case "webm":
      return b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3;
    default:
      return false;
  }
}

export function thumbMagicOk(b: Uint8Array): boolean {
  return magicMatches("jpg", b) || magicMatches("webp", b);
}

export function extOfKey(key: string): string {
  return key.split(".").pop() ?? "";
}
