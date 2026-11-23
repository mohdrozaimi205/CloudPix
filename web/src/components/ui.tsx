import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { X } from "lucide-react";
import { formatBytes } from "../lib/format";

// ---------------- Toast ----------------
type Toast = { id: number; text: string; tone: "ok" | "err" | "info" };
const ToastCtx = createContext<(text: string, tone?: Toast["tone"]) => void>(() => {});
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<Toast[]>([]);
  const push = useCallback((text: string, tone: Toast["tone"] = "info") => {
    const id = Date.now() + Math.random();
    setList((l) => [...l, { id, text, tone }]);
    setTimeout(() => setList((l) => l.filter((t) => t.id !== id)), 4200);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts">
        {list.map((t) => (
          <div key={t.id} className={`toast ${t.tone}`}>
            {t.text}
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

// ---------------- Modal ----------------
export function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: ReactNode; wide?: boolean }) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal glass ${wide ? "wide" : ""}`} role="dialog" aria-label={title}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Tutup">
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function PromptModal(props: {
  title: string;
  label: string;
  initial?: string;
  confirm: string;
  onSubmit: (v: string) => Promise<void> | void;
  onClose: () => void;
}) {
  const [v, setV] = useState(props.initial ?? "");
  const [busy, setBusy] = useState(false);
  return (
    <Modal title={props.title} onClose={props.onClose}>
      <form
        className="stack"
        onSubmit={async (e) => {
          e.preventDefault();
          if (!v.trim()) return;
          setBusy(true);
          try {
            await props.onSubmit(v.trim());
            props.onClose();
          } finally {
            setBusy(false);
          }
        }}
      >
        <label className="field">
          <span>{props.label}</span>
          <input autoFocus value={v} onChange={(e) => setV(e.target.value)} maxLength={255} />
        </label>
        <button className="btn primary" disabled={busy || !v.trim()}>
          {busy ? "Menyimpan…" : props.confirm}
        </button>
      </form>
    </Modal>
  );
}

export function ConfirmModal(props: { title: string; text: string; confirm: string; danger?: boolean; onConfirm: () => Promise<void> | void; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  return (
    <Modal title={props.title} onClose={props.onClose}>
      <p className="muted">{props.text}</p>
      <div className="row end">
        <button className="btn ghost" onClick={props.onClose}>
          Batal
        </button>
        <button
          className={`btn ${props.danger ? "danger" : "primary"}`}
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              await props.onConfirm();
              props.onClose();
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? "Sila tunggu…" : props.confirm}
        </button>
      </div>
    </Modal>
  );
}

// ---------------- Storage ring ----------------
export function StorageRing({ used, quota, size = 150 }: { used: number; quota: number; size?: number }) {
  const pct = quota ? Math.min(100, (used / quota) * 100) : 0;
  const r = 42;
  const c = 2 * Math.PI * r;
  const tone = pct >= 95 ? "var(--danger)" : pct >= 80 ? "var(--warn)" : "url(#ringGrad)";
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg viewBox="0 0 100 100">
        <defs>
          <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="#38bdf8" />
            <stop offset="1" stopColor="#2563eb" />
          </linearGradient>
        </defs>
        <circle cx="50" cy="50" r={r} className="ring-track" />
        <circle
          cx="50"
          cy="50"
          r={r}
          fill="none"
          stroke={tone}
          strokeWidth="9"
          strokeLinecap="round"
          strokeDasharray={`${(pct / 100) * c} ${c}`}
          transform="rotate(-90 50 50)"
          style={{ transition: "stroke-dasharray .8s ease" }}
        />
      </svg>
      <div className="ring-label">
        <b>{pct < 0.1 && used > 0 ? "<0.1" : pct.toFixed(pct < 10 ? 1 : 0)}%</b>
        <small>{formatBytes(used)}</small>
      </div>
    </div>
  );
}

export function Empty({ icon, title, text, action }: { icon: ReactNode; title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      {text && <p className="muted">{text}</p>}
      {action}
    </div>
  );
}
