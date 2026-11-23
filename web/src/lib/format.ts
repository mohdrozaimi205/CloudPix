export function formatBytes(n: number, digits = 1): string {
  if (!n) return "0 B";
  const u = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.min(Math.floor(Math.log(n) / Math.log(1024)), u.length - 1);
  const v = n / 1024 ** i;
  return `${v >= 100 || i === 0 ? Math.round(v) : v.toFixed(digits)} ${u[i]}`;
}

export function formatDuration(ms: number | null | undefined): string {
  if (!ms) return "";
  const s = Math.round(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, "0");
  return h ? `${h}:${String(m).padStart(2, "0")}:${sec}` : `${m}:${sec}`;
}

const dtf = new Intl.DateTimeFormat("ms-MY", { day: "numeric", month: "short", year: "numeric" });
const dtt = new Intl.DateTimeFormat("ms-MY", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
export const formatDate = (s: string) => dtf.format(new Date(s));
export const formatDateTime = (s: string) => dtt.format(new Date(s));

/** Kumpulan: Hari ini, Semalam, Minggu ini, Bulan ini, kemudian ikut tahun. */
export function groupLabel(iso: string, now = new Date()): string {
  const d = new Date(iso);
  const start = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const today = start(now);
  const t = start(d);
  const day = 86400000;
  if (t === today) return "Hari ini";
  if (t === today - day) return "Semalam";
  const weekStart = today - ((now.getDay() + 6) % 7) * day; // Isnin
  if (t >= weekStart) return "Minggu ini";
  if (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()) return "Bulan ini";
  return String(d.getFullYear());
}

export function warningFor(percent: number): { level: 0 | 80 | 90 | 95; text: string } {
  if (percent >= 100) return { level: 95, text: "Storan penuh. Upload disekat sehingga anda kosongkan ruang." };
  if (percent >= 95) return { level: 95, text: "Storan hampir penuh (95%). Kosongkan ruang segera." };
  if (percent >= 90) return { level: 90, text: "Storan sudah 90% digunakan." };
  if (percent >= 80) return { level: 80, text: "Storan sudah 80% digunakan." };
  return { level: 0, text: "" };
}
