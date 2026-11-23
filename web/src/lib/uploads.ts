// Barisan upload sebenar: hash SHA-256, thumbnail, PUT terus ke R2 dengan kemajuan bait sebenar,
// multipart untuk fail besar (4 bahagian serentak), cuba semula & sambung semula selepas putus talian.
import { useSyncExternalStore } from "react";
import { createSHA256 } from "hash-wasm";
import { api, ApiError, type InitResponse } from "./api";

export type UploadStatus = "queued" | "hashing" | "uploading" | "finishing" | "done" | "duplicate" | "error" | "cancelled";

export interface UploadItem {
  id: string;
  file: File;
  albumId?: string;
  status: UploadStatus;
  sent: number;
  error?: string;
  mediaId?: string;
  init?: InitResponse;
  sha256?: string;
}

let items: UploadItem[] = [];
const listeners = new Set<() => void>();
const xhrs = new Map<string, Set<XMLHttpRequest>>();
const CONCURRENCY = 2;
let onFinished: (() => void) | null = null;

function emit() {
  items = [...items];
  listeners.forEach((l) => l());
}
function patch(id: string, p: Partial<UploadItem>) {
  const it = items.find((x) => x.id === id);
  if (it) Object.assign(it, p);
  emit();
}

export function useUploads(): UploadItem[] {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => items,
  );
}

export function setUploadFinishedHandler(fn: () => void) {
  onFinished = fn;
}

const ACCEPT = /\.(jpe?g|png|webp|heic|heif|gif|mp4|m4v|mov|mkv|webm)$/i;
export const ACCEPT_ATTR = ".jpg,.jpeg,.png,.webp,.heic,.heif,.gif,.mp4,.m4v,.mov,.mkv,.webm,image/*,video/*";

export function enqueue(files: FileList | File[], albumId?: string): { added: number; rejected: string[] } {
  const rejected: string[] = [];
  let added = 0;
  for (const f of Array.from(files)) {
    if (!ACCEPT.test(f.name)) {
      rejected.push(f.name);
      continue;
    }
    items.push({ id: crypto.randomUUID(), file: f, albumId, status: "queued", sent: 0 });
    added++;
  }
  emit();
  pump();
  return { added, rejected };
}

export function retry(id: string) {
  patch(id, { status: "queued", error: undefined });
  pump();
}

export async function cancel(id: string) {
  const it = items.find((x) => x.id === id);
  if (!it) return;
  xhrs.get(id)?.forEach((x) => x.abort());
  patch(id, { status: "cancelled" });
  if (it.mediaId) await api.multipart("abort", { mediaId: it.mediaId }).catch(() => {});
}

export function clearFinished() {
  items = items.filter((x) => !["done", "duplicate", "cancelled"].includes(x.status));
  emit();
}

function pump() {
  const active = items.filter((x) => ["hashing", "uploading", "finishing"].includes(x.status)).length;
  const next = items.filter((x) => x.status === "queued").slice(0, Math.max(0, CONCURRENCY - active));
  next.forEach((it) => void run(it));
}

window.addEventListener("online", () => {
  items.filter((x) => x.status === "error" && x.error?.includes("sambungan")).forEach((x) => retry(x.id));
});

// ---------------------------------------------------------------

async function sha256(file: File, id: string): Promise<string> {
  const h = await createSHA256();
  h.init();
  const chunk = 8 * 1024 * 1024;
  for (let off = 0; off < file.size; off += chunk) {
    const buf = new Uint8Array(await file.slice(off, off + chunk).arrayBuffer());
    h.update(buf);
    if (items.find((x) => x.id === id)?.status === "cancelled") throw new Error("cancelled");
  }
  return h.digest("hex");
}

interface Meta {
  width?: number;
  height?: number;
  durationMs?: number;
  thumb?: Blob;
}

function toThumb(src: CanvasImageSource, w: number, h: number): Promise<Blob | undefined> {
  const max = 512;
  const s = Math.min(1, max / Math.max(w, h));
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(w * s));
  c.height = Math.max(1, Math.round(h * s));
  c.getContext("2d")!.drawImage(src, 0, 0, c.width, c.height);
  return new Promise((r) => c.toBlob((b) => r(b ?? undefined), "image/jpeg", 0.82));
}

async function readMeta(file: File): Promise<Meta> {
  const isVideo = /\.(mp4|m4v|mov|mkv|webm)$/i.test(file.name);
  try {
    if (!isVideo) {
      const bmp = await createImageBitmap(file); // HEIC gagal di kebanyakan pelayar → tiada thumbnail
      const thumb = await toThumb(bmp, bmp.width, bmp.height);
      const meta = { width: bmp.width, height: bmp.height, thumb };
      bmp.close();
      return meta;
    }
    return await new Promise<Meta>((resolve) => {
      const v = document.createElement("video");
      const url = URL.createObjectURL(file);
      const done = (m: Meta) => {
        URL.revokeObjectURL(url);
        resolve(m);
      };
      const t = setTimeout(() => done({}), 10000);
      v.muted = true;
      v.preload = "metadata";
      v.src = url;
      v.onloadedmetadata = () => {
        v.currentTime = Math.min(1, (v.duration || 2) / 2);
      };
      v.onseeked = async () => {
        clearTimeout(t);
        const thumb = v.videoWidth ? await toThumb(v, v.videoWidth, v.videoHeight) : undefined;
        done({
          width: v.videoWidth || undefined,
          height: v.videoHeight || undefined,
          durationMs: isFinite(v.duration) ? Math.round(v.duration * 1000) : undefined,
          thumb,
        });
      };
      v.onerror = () => {
        clearTimeout(t);
        done({});
      };
    });
  } catch {
    return {};
  }
}

function put(id: string, url: string, body: Blob, contentType: string | null, onProgress: (n: number) => void) {
  return new Promise<void>((resolve, reject) => {
    const x = new XMLHttpRequest();
    const set = xhrs.get(id) ?? new Set();
    set.add(x);
    xhrs.set(id, set);
    x.open("PUT", url);
    if (contentType) x.setRequestHeader("Content-Type", contentType);
    x.upload.onprogress = (e) => onProgress(e.loaded);
    x.onload = () => {
      set.delete(x);
      if (x.status >= 200 && x.status < 300) resolve();
      else reject(new Error(`Storan menolak upload (${x.status}).`));
    };
    x.onerror = () => {
      set.delete(x);
      reject(new Error("Tiada sambungan internet."));
    };
    x.onabort = () => {
      set.delete(x);
      reject(new Error("cancelled"));
    };
    x.send(body);
  });
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

async function withRetry<T>(fn: () => Promise<T>, tries = 5): Promise<T> {
  let last: unknown;
  for (let i = 0; i < tries; i++) {
    try {
      return await fn();
    } catch (e) {
      if ((e as Error).message === "cancelled") throw e;
      if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 429) throw e;
      last = e;
      if (!navigator.onLine) await new Promise((r) => window.addEventListener("online", r, { once: true }));
      await sleep(Math.min(30000, 1000 * 2 ** i));
    }
  }
  throw last;
}

async function run(it: UploadItem) {
  const { id, file } = it;
  const cancelled = () => items.find((x) => x.id === id)?.status === "cancelled";
  try {
    if (!it.init) {
      patch(id, { status: "hashing", sent: 0 });
      const hash = it.sha256 ?? (await sha256(file, id));
      const meta = await readMeta(file);
      const init = await withRetry(() =>
        api.uploadInit({
          filename: file.name,
          size: file.size,
          sha256: hash,
          albumId: it.albumId,
          width: meta.width,
          height: meta.height,
          durationMs: meta.durationMs,
          takenAt: file.lastModified ? new Date(file.lastModified).toISOString() : undefined,
          source: "web",
        }),
      );
      if (init.duplicate) {
        patch(id, { status: "duplicate", sha256: hash });
        return;
      }
      patch(id, { init, mediaId: init.mediaId, sha256: hash });
      if (meta.thumb) await put(id, init.thumbUploadUrl, meta.thumb, "image/jpeg", () => {}).catch(() => {});
    }
    const init = items.find((x) => x.id === id)!.init!;
    patch(id, { status: "uploading" });

    if (init.mode === "single") {
      await put(id, init.uploadUrl!, file, init.contentType, (n) => patch(id, { sent: n }));
      patch(id, { status: "finishing", sent: file.size });
      await withRetry(() => api.uploadComplete(init.mediaId));
    } else {
      await multipart(id, file, init, cancelled);
      patch(id, { status: "finishing", sent: file.size });
      await withRetry(() => api.multipart("complete", { mediaId: init.mediaId }));
    }
    patch(id, { status: "done" });
    onFinished?.();
  } catch (e) {
    if (cancelled()) return;
    const msg = (e as Error).message;
    const cur = items.find((x) => x.id === id);
    // upload mod single tidak boleh disambung → batalkan tempahan supaya kuota tidak terkunci
    if (cur?.init?.mode === "single" && !(e instanceof ApiError && e.code === "SIZE_MISMATCH")) {
      await api.multipart("abort", { mediaId: cur.init.mediaId }).catch(() => {});
      patch(id, { init: undefined, mediaId: undefined });
    }
    if (e instanceof ApiError && e.code === "UPLOAD_EXPIRED") patch(id, { init: undefined, mediaId: undefined });
    patch(id, { status: "error", error: msg });
  } finally {
    pump();
  }
}

async function multipart(id: string, file: File, init: InitResponse, cancelled: () => boolean) {
  const partSize = init.partSize!;
  const partCount = init.partCount!;
  // Sambung semula: tanya R2 bahagian mana sudah diterima
  const listed = await withRetry(() =>
    api.multipart<{ parts: { partNumber: number; size: number }[] }>("list", { mediaId: init.mediaId }),
  );
  const done = new Map(listed.parts.map((p) => [p.partNumber, p.size]));
  const inflight = new Map<number, number>();
  const report = () => {
    let s = 0;
    done.forEach((v) => (s += v));
    inflight.forEach((v) => (s += v));
    patch(id, { sent: Math.min(s, file.size) });
  };
  report();

  const todo: number[] = [];
  for (let n = 1; n <= partCount; n++) if (!done.has(n)) todo.push(n);
  const urls = new Map<number, string>();

  async function urlFor(n: number) {
    if (!urls.has(n)) {
      const batch = todo.filter((x) => !urls.has(x) && x >= n).slice(0, 20);
      const r = await withRetry(() =>
        api.multipart<{ urls: Record<string, string> }>("sign", { mediaId: init.mediaId, partNumbers: batch }),
      );
      Object.entries(r.urls).forEach(([k, v]) => urls.set(Number(k), v));
    }
    return urls.get(n)!;
  }

  let cursor = 0;
  async function worker() {
    while (cursor < todo.length) {
      if (cancelled()) throw new Error("cancelled");
      const n = todo[cursor++];
      const start = (n - 1) * partSize;
      const blob = file.slice(start, Math.min(start + partSize, file.size));
      await withRetry(async () => {
        try {
          await put(id, await urlFor(n), blob, null, (b) => {
            inflight.set(n, b);
            report();
          });
        } catch (e) {
          urls.delete(n); // URL mungkin luput; minta baru
          inflight.delete(n);
          report();
          throw e;
        }
      }, 8);
      inflight.delete(n);
      done.set(n, blob.size);
      report();
    }
  }
  await Promise.all([worker(), worker(), worker(), worker()]);
}
