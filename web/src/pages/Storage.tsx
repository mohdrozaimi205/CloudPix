import { AlertTriangle } from "lucide-react";
import { Gallery } from "../components/Gallery";
import { PageHead } from "../components/Layout";
import { StorageRing } from "../components/ui";
import { formatBytes, warningFor } from "../lib/format";
import { useStats } from "../lib/queries";
import { MonthlyChart } from "./Dashboard";

export function StoragePage() {
  const { data: s } = useStats();
  const quota = s?.quota_bytes ?? 1024 ** 4;
  const seg = (n: number) => `${quota ? (n / quota) * 100 : 0}%`;
  const warn = warningFor(s?.used_percent ?? 0);
  const others = Math.max(0, (s?.used_bytes ?? 0) - (s?.photo_bytes ?? 0) - (s?.video_bytes ?? 0) - (s?.trash_bytes ?? 0));
  return (
    <div className="page">
      <PageHead title="Storan" sub="Dikira dari saiz sebenar fail yang telah disahkan" />
      {warn.level > 0 && (
        <div className={`notice ${warn.level >= 95 ? "err" : "warn"} row`}>
          <AlertTriangle size={18} /> {warn.text}
        </div>
      )}
      <div className="card glass storage-big">
        <StorageRing used={s?.used_bytes ?? 0} quota={quota} size={190} />
        <div className="stack grow">
          <div className="seg-bar">
            <i className="p" style={{ width: seg(s?.photo_bytes ?? 0) }} />
            <i className="v" style={{ width: seg(s?.video_bytes ?? 0) }} />
            <i className="t" style={{ width: seg((s?.trash_bytes ?? 0) + others) }} />
          </div>
          <div className="legend-grid">
            <div>
              <i className="p" /> Foto <b>{formatBytes(s?.photo_bytes ?? 0, 2)}</b>
              <small className="muted">{s?.photo_count ?? 0} fail</small>
            </div>
            <div>
              <i className="v" /> Video <b>{formatBytes(s?.video_bytes ?? 0, 2)}</b>
              <small className="muted">{s?.video_count ?? 0} fail</small>
            </div>
            <div>
              <i className="t" /> Baru Dipadam <b>{formatBytes(s?.trash_bytes ?? 0, 2)}</b>
              <small className="muted">{s?.trash_count ?? 0} fail · dipadam auto selepas 30 hari</small>
            </div>
            <div>
              <i className="f" /> Baki <b>{formatBytes(s?.available_bytes ?? 0, 2)}</b>
              <small className="muted">daripada {formatBytes(quota, 0)}</small>
            </div>
          </div>
        </div>
      </div>
      <div className="card glass">
        <h3>Analitik upload bulanan</h3>
        <MonthlyChart />
      </div>
      <h2 className="section-title">Fail terbesar</h2>
      <GalleryLargest />
    </div>
  );
}

function GalleryLargest() {
  return <Gallery base={{ view: "all" }} initialSort="size" emptyText="Belum ada fail." />;
}
