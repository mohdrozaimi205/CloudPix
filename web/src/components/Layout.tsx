import { useRef, useState, type ReactNode } from "react";
import { NavLink, useLocation } from "react-router-dom";
import {
  Cloud, Film, FolderOpen, HardDrive, Heart, Image, LayoutDashboard, MoreHorizontal, Settings, Trash2, UploadCloud,
} from "lucide-react";
import { ACCEPT_ATTR, enqueue } from "../lib/uploads";
import { useStats } from "../lib/queries";
import { formatBytes } from "../lib/format";
import { UploadPanel } from "./UploadPanel";
import { useToast } from "./ui";

const NAV = [
  { to: "/", label: "Dashboard", icon: LayoutDashboard },
  { to: "/photos", label: "Foto", icon: Image },
  { to: "/videos", label: "Video", icon: Film },
  { to: "/albums", label: "Album", icon: FolderOpen },
  { to: "/favourites", label: "Kegemaran", icon: Heart },
  { to: "/trash", label: "Baru Dipadam", icon: Trash2 },
  { to: "/storage", label: "Storan", icon: HardDrive },
  { to: "/settings", label: "Tetapan", icon: Settings },
];

export function useUploadPicker(albumId?: string) {
  const toast = useToast();
  const ref = useRef<HTMLInputElement>(null);
  const input = (
    <input
      ref={ref}
      type="file"
      multiple
      hidden
      accept={ACCEPT_ATTR}
      onChange={(e) => {
        if (e.target.files?.length) {
          const r = enqueue(e.target.files, albumId);
          if (r.rejected.length) toast(`${r.rejected.length} fail ditolak (jenis tidak disokong)`, "err");
        }
        e.target.value = "";
      }}
    />
  );
  return { open: () => ref.current?.click(), input };
}

export function Layout({ children }: { children: ReactNode }) {
  const picker = useUploadPicker();
  const stats = useStats();
  const toast = useToast();
  const [drag, setDrag] = useState(false);
  const [more, setMore] = useState(false);
  const loc = useLocation();
  const pct = stats.data ? Math.min(100, stats.data.used_percent) : 0;

  return (
    <div
      className="shell"
      onDragOver={(e) => {
        if (e.dataTransfer.types.includes("Files")) {
          e.preventDefault();
          setDrag(true);
        }
      }}
      onDragLeave={(e) => e.currentTarget === e.target && setDrag(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDrag(false);
        if (e.dataTransfer.files.length) {
          const albumId = loc.pathname.startsWith("/albums/") ? loc.pathname.split("/")[2] : undefined;
          const r = enqueue(e.dataTransfer.files, albumId);
          if (r.rejected.length) toast(`${r.rejected.length} fail ditolak (jenis tidak disokong)`, "err");
        }
      }}
    >
      <div className="aurora" aria-hidden />
      <aside className="sidebar glass">
        <div className="brand">
          <div className="logo">
            <Cloud size={20} />
          </div>
          <span>CloudPix</span>
        </div>
        <button className="btn primary upload-btn" onClick={picker.open}>
          <UploadCloud size={18} /> Upload
        </button>
        <nav>
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === "/"} className="nav-item">
              <Icon size={19} /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="side-storage">
          <div className="row between small">
            <span>Storan</span>
            <span className="muted">{pct.toFixed(1)}%</span>
          </div>
          <div className="bar thin">
            <i style={{ width: `${pct}%` }} className={pct >= 95 ? "danger" : pct >= 80 ? "warn" : ""} />
          </div>
          <small className="muted">
            {formatBytes(stats.data?.used_bytes ?? 0)} daripada {formatBytes(stats.data?.quota_bytes ?? 1024 ** 4)}
          </small>
        </div>
      </aside>

      <main className="content">{children}</main>

      <nav className="bottom-nav glass">
        {NAV.slice(0, 4).map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end={to === "/"} className="bn-item" onClick={() => setMore(false)}>
            <Icon size={21} />
            <span>{label}</span>
          </NavLink>
        ))}
        <button className={`bn-item ${more ? "active" : ""}`} onClick={() => setMore(!more)}>
          <MoreHorizontal size={21} />
          <span>Lagi</span>
        </button>
      </nav>
      {more && (
        <div className="more-sheet glass" onClick={() => setMore(false)}>
          {NAV.slice(4).map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className="nav-item">
              <Icon size={19} /> {label}
            </NavLink>
          ))}
        </div>
      )}
      <button className="fab" onClick={picker.open} aria-label="Upload">
        <UploadCloud size={22} />
      </button>

      {picker.input}
      <UploadPanel />
      {drag && (
        <div className="drop-overlay">
          <UploadCloud size={48} />
          <b>Lepaskan untuk upload</b>
        </div>
      )}
    </div>
  );
}

export function PageHead({ title, sub, children }: { title: string; sub?: string; children?: ReactNode }) {
  return (
    <header className="page-head">
      <div>
        <h1>{title}</h1>
        {sub && <p className="muted">{sub}</p>}
      </div>
      <div className="row">{children}</div>
    </header>
  );
}
