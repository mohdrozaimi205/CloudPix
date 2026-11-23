// Cache URL bertandatangan + gabungkan permintaan thumbnail dalam satu panggilan.
import { api } from "./api";

type Entry = { url: string | null; exp: number };
const cache = new Map<string, Entry>();
const waiting = new Map<string, ((u: string | null) => void)[]>();
let timer: number | null = null;

const key = (id: string, v: string) => `${v}:${id}`;

function fresh(e?: Entry) {
  return e && e.exp - Date.now() > 120_000;
}

async function flush() {
  timer = null;
  const ids = [...waiting.keys()];
  const cbs = new Map(waiting);
  waiting.clear();
  for (let i = 0; i < ids.length; i += 200) {
    const batch = ids.slice(i, i + 200);
    try {
      const r = await api.mediaUrl(batch, "thumb");
      for (const id of batch) {
        const u = r.urls[id];
        const entry = { url: u?.url ?? null, exp: u ? new Date(u.expiresAt).getTime() : Date.now() + 60_000 };
        cache.set(key(id, "thumb"), entry);
        cbs.get(id)?.forEach((cb) => cb(entry.url));
      }
    } catch {
      for (const id of batch) cbs.get(id)?.forEach((cb) => cb(null));
    }
  }
}

export function thumbUrl(id: string): Promise<string | null> {
  const c = cache.get(key(id, "thumb"));
  if (fresh(c)) return Promise.resolve(c!.url);
  return new Promise((resolve) => {
    const list = waiting.get(id) ?? [];
    list.push(resolve);
    waiting.set(id, list);
    if (timer === null) timer = window.setTimeout(flush, 30);
  });
}

export async function mediaUrl(id: string, variant: "original" | "download" = "original"): Promise<string | null> {
  const c = cache.get(key(id, variant));
  if (variant === "original" && fresh(c)) return c!.url;
  const r = await api.mediaUrl([id], variant);
  const u = r.urls[id];
  if (!u?.url) return null;
  cache.set(key(id, variant), { url: u.url, exp: new Date(u.expiresAt).getTime() });
  return u.url;
}

export async function downloadMedia(ids: string[]) {
  for (let i = 0; i < ids.length; i += 50) {
    const r = await api.mediaUrl(ids.slice(i, i + 50), "download");
    for (const id of ids.slice(i, i + 50)) {
      const url = r.urls[id]?.url;
      if (!url) continue;
      const a = document.createElement("a");
      a.href = url;
      a.rel = "noopener";
      document.body.appendChild(a);
      a.click();
      a.remove();
      await new Promise((res) => setTimeout(res, 400));
    }
  }
}
