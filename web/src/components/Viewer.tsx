import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft, ChevronLeft, ChevronRight, Download, FolderInput, Heart, Info, Pencil, RotateCcw, Share2, Trash2, ZoomIn, ZoomOut,
} from "lucide-react";
import { formatBytes, formatDateTime, formatDuration } from "../lib/format";
import { hasThumb, type Media } from "../lib/types";
import { mediaUrl, thumbUrl } from "../lib/urls";
import { useMediaActions } from "./actions";

const BROWSER_IMG = /^image\/(jpeg|png|webp|gif)$/;

export function Viewer({ items, index, onIndex, onClose }: { items: Media[]; index: number; onIndex: (i: number) => void; onClose: () => void }) {
  const m = items[index];
  const actions = useMediaActions();
  const [src, setSrc] = useState<string | null>(null);
  const [fallback, setFallback] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [info, setInfo] = useState(false);
  const drag = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    if (!m) return;
    let alive = true;
    setSrc(null);
    setFallback(null);
    setZoom(1);
    setPan({ x: 0, y: 0 });
    (async () => {
      if (hasThumb(m)) thumbUrl(m.id).then((u) => alive && setFallback(u));
      if (m.kind === "video" || BROWSER_IMG.test(m.mime_type)) {
        const u = await mediaUrl(m.id).catch(() => null);
        if (alive) setSrc(u);
      }
    })();
    return () => {
      alive = false;
    };
  }, [m]);

  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement).tagName === "INPUT") return;
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowRight" && index < items.length - 1) onIndex(index + 1);
      if (e.key === "ArrowLeft" && index > 0) onIndex(index - 1);
      if (e.key === "+" || e.key === "=") setZoom((z) => Math.min(6, z + 0.5));
      if (e.key === "-") setZoom((z) => Math.max(1, z - 0.5));
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [index, items.length, onClose, onIndex]);

  if (!m) return null;
  const inTrash = Boolean(m.deleted_at);

  return (
    <div className="viewer">
      <div className="viewer-top">
        <button className="icon-btn light" onClick={onClose} aria-label="Kembali">
          <ArrowLeft size={20} />
        </button>
        <div className="viewer-title">
          <b>{m.filename}</b>
          <small>{formatDateTime(m.taken_at ?? m.created_at)}</small>
        </div>
        <div className="row">
          {m.kind === "image" && (
            <>
              <button className="icon-btn light" onClick={() => setZoom((z) => Math.max(1, z - 0.5))} aria-label="Zum keluar">
                <ZoomOut size={19} />
              </button>
              <button className="icon-btn light" onClick={() => setZoom((z) => Math.min(6, z + 0.5))} aria-label="Zum masuk">
                <ZoomIn size={19} />
              </button>
            </>
          )}
          {!inTrash ? (
            <>
              <button className={`icon-btn light ${m.is_favourite ? "fav" : ""}`} onClick={() => actions.favourite([m])} aria-label="Kegemaran">
                <Heart size={19} fill={m.is_favourite ? "currentColor" : "none"} />
              </button>
              <button className="icon-btn light" onClick={() => actions.share(m)} aria-label="Kongsi">
                <Share2 size={19} />
              </button>
              <button className="icon-btn light" onClick={() => actions.download([m])} aria-label="Muat turun">
                <Download size={19} />
              </button>
              <button className="icon-btn light" onClick={() => actions.rename(m)} aria-label="Tukar nama">
                <Pencil size={19} />
              </button>
              <button className="icon-btn light" onClick={() => actions.move([m])} aria-label="Pindah album">
                <FolderInput size={19} />
              </button>
              <button className="icon-btn light" onClick={() => actions.trash([m]).then(onClose)} aria-label="Padam">
                <Trash2 size={19} />
              </button>
            </>
          ) : (
            <>
              <button className="icon-btn light" onClick={() => actions.restore([m]).then(onClose)} aria-label="Pulihkan">
                <RotateCcw size={19} />
              </button>
              <button className="icon-btn light" onClick={() => actions.purge([m])} aria-label="Padam kekal">
                <Trash2 size={19} />
              </button>
            </>
          )}
          <button className={`icon-btn light ${info ? "on" : ""}`} onClick={() => setInfo(!info)} aria-label="Maklumat">
            <Info size={19} />
          </button>
        </div>
      </div>

      <div
        className="viewer-stage"
        onWheel={(e) => m.kind === "image" && setZoom((z) => Math.min(6, Math.max(1, z - e.deltaY / 400)))}
        onDoubleClick={() => m.kind === "image" && setZoom((z) => (z > 1 ? 1 : 2.5))}
        onPointerDown={(e) => zoom > 1 && (drag.current = { x: e.clientX - pan.x, y: e.clientY - pan.y })}
        onPointerMove={(e) => drag.current && setPan({ x: e.clientX - drag.current.x, y: e.clientY - drag.current.y })}
        onPointerUp={() => (drag.current = null)}
        onPointerLeave={() => (drag.current = null)}
      >
        {m.kind === "video" ? (
          src ? (
            <video key={m.id} src={src} controls autoPlay playsInline poster={fallback ?? undefined} className="viewer-media" />
          ) : (
            <div className="spinner" />
          )
        ) : src || fallback ? (
          <img
            src={src ?? fallback!}
            alt={m.filename}
            className="viewer-media"
            draggable={false}
            style={{ transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`, cursor: zoom > 1 ? "grab" : "zoom-in" }}
          />
        ) : !BROWSER_IMG.test(m.mime_type) ? (
          <div className="viewer-note">
            Pelayar ini tidak boleh papar {m.mime_type.split("/")[1].toUpperCase()}.
            <button className="btn primary" onClick={() => actions.download([m])}>
              <Download size={16} /> Muat turun kualiti asal
            </button>
          </div>
        ) : (
          <div className="spinner" />
        )}
        {index > 0 && (
          <button className="nav-btn left" onClick={() => onIndex(index - 1)} aria-label="Sebelum">
            <ChevronLeft size={28} />
          </button>
        )}
        {index < items.length - 1 && (
          <button className="nav-btn right" onClick={() => onIndex(index + 1)} aria-label="Seterusnya">
            <ChevronRight size={28} />
          </button>
        )}
      </div>

      {info && (
        <aside className="viewer-info glass">
          <h4>Maklumat</h4>
          <dl>
            <dt>Nama</dt>
            <dd>{m.filename}</dd>
            <dt>Saiz</dt>
            <dd>{formatBytes(m.size_bytes, 2)}</dd>
            {m.width && (
              <>
                <dt>Resolusi</dt>
                <dd>
                  {m.width} × {m.height}
                </dd>
              </>
            )}
            {m.duration_ms ? (
              <>
                <dt>Durasi</dt>
                <dd>{formatDuration(m.duration_ms)}</dd>
              </>
            ) : null}
            <dt>Jenis</dt>
            <dd>{m.mime_type}</dd>
            <dt>Diupload</dt>
            <dd>{formatDateTime(m.created_at)}</dd>
            {m.taken_at && (
              <>
                <dt>Tarikh fail</dt>
                <dd>{formatDateTime(m.taken_at)}</dd>
              </>
            )}
            <dt>Sumber</dt>
            <dd>{m.source}</dd>
          </dl>
        </aside>
      )}
      {actions.modal}
    </div>
  );
}
