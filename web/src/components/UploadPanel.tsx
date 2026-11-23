import { useState } from "react";
import { AlertCircle, CheckCircle2, ChevronDown, ChevronUp, Copy, Loader2, RotateCw, X } from "lucide-react";
import { formatBytes } from "../lib/format";
import { cancel, clearFinished, retry, useUploads, type UploadItem } from "../lib/uploads";

const LABEL: Record<UploadItem["status"], string> = {
  queued: "Menunggu",
  hashing: "Menyemak fail…",
  uploading: "Mengupload",
  finishing: "Mengesahkan…",
  done: "Siap",
  duplicate: "Sudah wujud (duplikat)",
  error: "Gagal",
  cancelled: "Dibatalkan",
};

export function UploadPanel() {
  const items = useUploads();
  const [open, setOpen] = useState(true);
  if (items.length === 0) return null;
  const active = items.filter((i) => ["queued", "hashing", "uploading", "finishing"].includes(i.status));
  const totalBytes = active.reduce((s, i) => s + i.file.size, 0);
  const sentBytes = active.reduce((s, i) => s + i.sent, 0);

  return (
    <div className="upload-panel glass">
      <div className="upload-head" onClick={() => setOpen(!open)}>
        <div>
          <b>{active.length ? `Mengupload ${active.length} fail` : "Upload selesai"}</b>
          {active.length > 0 && (
            <small>
              {formatBytes(sentBytes)} / {formatBytes(totalBytes)}
            </small>
          )}
        </div>
        <div className="row">
          {!active.length && (
            <button className="icon-btn" onClick={(e) => (e.stopPropagation(), clearFinished())} aria-label="Tutup">
              <X size={16} />
            </button>
          )}
          {open ? <ChevronDown size={18} /> : <ChevronUp size={18} />}
        </div>
      </div>
      {active.length > 0 && (
        <div className="bar">
          <i style={{ width: `${totalBytes ? (sentBytes / totalBytes) * 100 : 0}%` }} />
        </div>
      )}
      {open && (
        <ul className="upload-list">
          {items.map((it) => (
            <li key={it.id}>
              <div className="upload-icon">
                {it.status === "done" ? (
                  <CheckCircle2 size={18} className="ok" />
                ) : it.status === "error" ? (
                  <AlertCircle size={18} className="err" />
                ) : it.status === "duplicate" ? (
                  <Copy size={18} className="muted" />
                ) : it.status === "cancelled" ? (
                  <X size={18} className="muted" />
                ) : (
                  <Loader2 size={18} className="spin" />
                )}
              </div>
              <div className="upload-body">
                <span className="name">{it.file.name}</span>
                <small className={it.status === "error" ? "err" : "muted"}>
                  {it.status === "error" ? it.error : LABEL[it.status]}
                  {it.status === "uploading" && ` · ${Math.floor((it.sent / it.file.size) * 100)}% · ${formatBytes(it.file.size)}`}
                </small>
                {it.status === "uploading" && (
                  <div className="bar thin">
                    <i style={{ width: `${(it.sent / it.file.size) * 100}%` }} />
                  </div>
                )}
              </div>
              {it.status === "error" && (
                <button className="icon-btn" onClick={() => retry(it.id)} aria-label="Cuba semula">
                  <RotateCw size={16} />
                </button>
              )}
              {["queued", "hashing", "uploading", "error"].includes(it.status) && (
                <button className="icon-btn" onClick={() => cancel(it.id)} aria-label="Batal">
                  <X size={16} />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
