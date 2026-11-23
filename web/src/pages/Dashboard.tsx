import { useState } from "react";
import { Link } from "react-router-dom";
import { AlertTriangle, Clock, Film, Heart, Image, UploadCloud } from "lucide-react";
import { PageHead, useUploadPicker } from "../components/Layout";
import { Thumb } from "../components/Thumb";
import { StorageRing } from "../components/ui";
import { Viewer } from "../components/Viewer";
import { formatBytes, warningFor } from "../lib/format";
import { useMediaSample, useMonthly, useStats } from "../lib/queries";
import { supabase } from "../lib/supabase";
import type { Media } from "../lib/types";

const base = () => supabase.from("media").select("*").eq("status", "ready").is("deleted_at", null);

export function Dashboard() {
  const stats = useStats();
  const picker = useUploadPicker();
  const recent = useMediaSample("recent", () => base().order("created_at", { ascending: false }).limit(12));
  const accessed = useMediaSample("accessed", () =>
    base().not("last_accessed_at", "is", null).order("last_accessed_at", { ascending: false }).limit(12),
  );
  const favs = useMediaSample("favs", () => base().eq("is_favourite", true).order("created_at", { ascending: false }).limit(12));
  const s = stats.data;
  const warn = warningFor(s?.used_percent ?? 0);

  return (
    <div className="page">
      <PageHead title="Dashboard" sub="Ringkasan storan awan anda">
        <button className="btn primary big" onClick={picker.open}>
          <UploadCloud size={20} /> Upload
        </button>
      </PageHead>
      {picker.input}

      {warn.level > 0 && (
        <div className={`notice ${warn.level >= 95 ? "err" : "warn"} row`}>
          <AlertTriangle size={18} /> {warn.text}{" "}
          <Link to="/storage" className="link">
            Urus storan
          </Link>
        </div>
      )}

      <div className="dash-grid">
        <div className="card glass storage-card">
          <StorageRing used={s?.used_bytes ?? 0} quota={s?.quota_bytes ?? 1024 ** 4} size={168} />
          <div className="stack tight">
            <span className="muted small">Jumlah storan</span>
            <b className="stat-xl">{formatBytes(s?.quota_bytes ?? 1024 ** 4, 0)}</b>
            <div className="kv">
              <span>Digunakan</span>
              <b>{formatBytes(s?.used_bytes ?? 0, 2)}</b>
            </div>
            <div className="kv">
              <span>Baki</span>
              <b>{formatBytes(s?.available_bytes ?? 0, 2)}</b>
            </div>
          </div>
        </div>
        <Link to="/photos" className="card glass stat-card">
          <div className="stat-icon blue">
            <Image size={22} />
          </div>
          <b className="stat-xl">{(s?.photo_count ?? 0).toLocaleString("ms-MY")}</b>
          <span className="muted">Foto · {formatBytes(s?.photo_bytes ?? 0)}</span>
        </Link>
        <Link to="/videos" className="card glass stat-card">
          <div className="stat-icon violet">
            <Film size={22} />
          </div>
          <b className="stat-xl">{(s?.video_count ?? 0).toLocaleString("ms-MY")}</b>
          <span className="muted">Video · {formatBytes(s?.video_bytes ?? 0)}</span>
        </Link>
        <Link to="/favourites" className="card glass stat-card">
          <div className="stat-icon pink">
            <Heart size={22} />
          </div>
          <b className="stat-xl">{(s?.favourite_count ?? 0).toLocaleString("ms-MY")}</b>
          <span className="muted">Kegemaran</span>
        </Link>
      </div>

      <div className="card glass">
        <h3>Upload 12 bulan terakhir</h3>
        <MonthlyChart />
      </div>

      <Strip title="Upload terkini" icon={<UploadCloud size={18} />} items={recent.data} link="/photos" />
      <Strip title="Baru dibuka" icon={<Clock size={18} />} items={accessed.data} />
      <Strip title="Kegemaran" icon={<Heart size={18} />} items={favs.data} link="/favourites" />
    </div>
  );
}

function Strip({ title, icon, items, link }: { title: string; icon: React.ReactNode; items?: Media[]; link?: string }) {
  const [idx, setIdx] = useState<number | null>(null);
  if (!items?.length) return null;
  return (
    <section className="strip">
      <div className="row between">
        <h3 className="row">
          {icon} {title}
        </h3>
        {link && (
          <Link to={link} className="link small">
            Lihat semua
          </Link>
        )}
      </div>
      <div className="strip-row">
        {items.map((m, i) => (
          <div key={m.id} className="tile" onClick={() => setIdx(i)}>
            <Thumb m={m} />
          </div>
        ))}
      </div>
      {idx !== null && <Viewer items={items} index={idx} onIndex={setIdx} onClose={() => setIdx(null)} />}
    </section>
  );
}

export function MonthlyChart() {
  const q = useMonthly();
  const months: { key: string; label: string; image: number; video: number }[] = [];
  const now = new Date();
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    months.push({
      key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`,
      label: d.toLocaleString("ms-MY", { month: "short" }),
      image: 0,
      video: 0,
    });
  }
  for (const r of q.data ?? []) {
    const m = months.find((x) => x.key === r.month.slice(0, 7));
    if (m) m[r.kind] += Number(r.total_bytes);
  }
  const max = Math.max(1, ...months.map((m) => m.image + m.video));
  return (
    <div className="chart">
      <div className="bars">
        {months.map((m) => (
          <div key={m.key} className="bar-col" title={`${m.label}: foto ${formatBytes(m.image)}, video ${formatBytes(m.video)}`}>
            <div className="bar-stack">
              <i className="v" style={{ height: `${(m.video / max) * 100}%` }} />
              <i className="p" style={{ height: `${(m.image / max) * 100}%` }} />
            </div>
            <small>{m.label}</small>
          </div>
        ))}
      </div>
      <div className="legend">
        <span>
          <i className="p" /> Foto
        </span>
        <span>
          <i className="v" /> Video
        </span>
        <span className="muted">Paling tinggi: {formatBytes(max)}</span>
      </div>
    </div>
  );
}
