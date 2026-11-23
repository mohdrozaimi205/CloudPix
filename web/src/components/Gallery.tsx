import { useEffect, useMemo, useRef, useState } from "react";
import {
  CheckCircle2, Circle, Download, FolderInput, Heart, ImageOff, Play, RotateCcw, Search, SlidersHorizontal, Trash2, X,
} from "lucide-react";
import { formatBytes, formatDuration, groupLabel } from "../lib/format";
import { useMediaList } from "../lib/queries";
import type { Media, MediaFilter, Sort } from "../lib/types";
import { useMediaActions } from "./actions";
import { Thumb } from "./Thumb";
import { Empty } from "./ui";
import { Viewer } from "./Viewer";

export function Gallery({ base, emptyText, showKindFilter = true, initialSort = "newest" }: { base: Omit<MediaFilter, "sort">; emptyText: string; showKindFilter?: boolean; initialSort?: Sort }) {
  const [search, setSearch] = useState("");
  const [debounced, setDebounced] = useState("");
  const [sort, setSort] = useState<Sort>(initialSort);
  const [kind, setKind] = useState<"" | "image" | "video">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [viewIndex, setViewIndex] = useState<number | null>(null);
  const actions = useMediaActions();

  useEffect(() => {
    const t = setTimeout(() => setDebounced(search), 300);
    return () => clearTimeout(t);
  }, [search]);

  const filter: MediaFilter = { ...base, search: debounced, sort, kind, from, to };
  const q = useMediaList(filter);
  const items = useMemo(() => q.data?.pages.flat() ?? [], [q.data]);
  const isTrash = base.view === "trash";

  // Infinite scroll
  const sentinel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = sentinel.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => {
      if (e[0].isIntersecting && q.hasNextPage && !q.isFetchingNextPage) void q.fetchNextPage();
    }, { rootMargin: "800px" });
    io.observe(el);
    return () => io.disconnect();
  }, [q]);

  useEffect(() => setSelected(new Set()), [base.view, base.albumId]);

  const groups = useMemo(() => {
    if (sort !== "newest" && sort !== "oldest") return [{ label: "", items }];
    const out: { label: string; items: Media[] }[] = [];
    for (const m of items) {
      const label = groupLabel(isTrash ? m.deleted_at! : m.created_at);
      if (out.at(-1)?.label !== label) out.push({ label, items: [] });
      out.at(-1)!.items.push(m);
    }
    return out;
  }, [items, sort, isTrash]);

  const sel = items.filter((m) => selected.has(m.id));
  const toggle = (id: string) =>
    setSelected((s) => {
      const n = new Set(s);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  const clear = () => setSelected(new Set());
  const after = (p: Promise<unknown>) => p.then(clear).catch(() => {});

  return (
    <div className="gallery">
      <div className="toolbar glass">
        <div className="search">
          <Search size={17} />
          <input placeholder="Cari nama fail…" value={search} onChange={(e) => setSearch(e.target.value)} />
          {search && (
            <button className="icon-btn" onClick={() => setSearch("")} aria-label="Kosongkan">
              <X size={15} />
            </button>
          )}
        </div>
        <select value={sort} onChange={(e) => setSort(e.target.value as Sort)} aria-label="Susun">
          <option value="newest">Terbaru</option>
          <option value="oldest">Terlama</option>
          <option value="size">Saiz terbesar</option>
          <option value="name">Nama A–Z</option>
        </select>
        <button className={`btn ghost ${showFilters ? "on" : ""}`} onClick={() => setShowFilters(!showFilters)}>
          <SlidersHorizontal size={16} /> Tapis
        </button>
      </div>
      {showFilters && (
        <div className="filters glass">
          {showKindFilter && (
            <label className="field inline">
              <span>Jenis</span>
              <select value={kind} onChange={(e) => setKind(e.target.value as "" | "image" | "video")}>
                <option value="">Semua</option>
                <option value="image">Foto</option>
                <option value="video">Video</option>
              </select>
            </label>
          )}
          <label className="field inline">
            <span>Dari</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field inline">
            <span>Hingga</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          {(kind || from || to) && (
            <button className="btn ghost" onClick={() => (setKind(""), setFrom(""), setTo(""))}>
              Set semula
            </button>
          )}
        </div>
      )}

      {sel.length > 0 && (
        <div className="selbar glass">
          <button className="icon-btn" onClick={clear} aria-label="Batal pilih">
            <X size={18} />
          </button>
          <b>{sel.length} dipilih</b>
          <button className="btn ghost small" onClick={() => setSelected(new Set(items.map((m) => m.id)))}>
            Pilih semua
          </button>
          <div className="spacer" />
          {!isTrash ? (
            <>
              <button className="icon-btn" title="Kegemaran" onClick={() => after(actions.favourite(sel))}>
                <Heart size={18} />
              </button>
              <button className="icon-btn" title="Muat turun" onClick={() => actions.download(sel)}>
                <Download size={18} />
              </button>
              <button className="icon-btn" title="Pindah ke album" onClick={() => actions.move(sel)}>
                <FolderInput size={18} />
              </button>
              <button className="icon-btn danger" title="Padam" onClick={() => after(actions.trash(sel))}>
                <Trash2 size={18} />
              </button>
            </>
          ) : (
            <>
              <button className="btn ghost small" onClick={() => after(actions.restore(sel))}>
                <RotateCcw size={16} /> Pulihkan
              </button>
              <button className="btn danger small" onClick={() => actions.purge(sel)}>
                <Trash2 size={16} /> Padam kekal
              </button>
            </>
          )}
        </div>
      )}

      {q.isLoading ? (
        <div className="grid">
          {Array.from({ length: 18 }).map((_, i) => (
            <div key={i} className="tile skeleton" />
          ))}
        </div>
      ) : q.isError ? (
        <Empty icon={<ImageOff />} title="Gagal memuatkan" text={(q.error as Error).message} action={<button className="btn primary" onClick={() => q.refetch()}>Cuba lagi</button>} />
      ) : items.length === 0 ? (
        <Empty icon={<ImageOff />} title={debounced || from || to || kind ? "Tiada hasil" : "Kosong"} text={debounced || from || to || kind ? "Cuba carian atau tapisan lain." : emptyText} />
      ) : (
        groups.map((g) => (
          <section key={g.label || "all"}>
            {g.label && <h3 className="group-label">{g.label}</h3>}
            <div className="grid">
              {g.items.map((m) => {
                const isSel = selected.has(m.id);
                return (
                  <div
                    key={m.id}
                    className={`tile ${isSel ? "selected" : ""}`}
                    onClick={() => (selected.size ? toggle(m.id) : setViewIndex(items.indexOf(m)))}
                    onContextMenu={(e) => (e.preventDefault(), toggle(m.id))}
                  >
                    <Thumb m={m} />
                    <button
                      className="tile-check"
                      onClick={(e) => (e.stopPropagation(), toggle(m.id))}
                      aria-label={isSel ? "Nyahpilih" : "Pilih"}
                    >
                      {isSel ? <CheckCircle2 size={22} /> : <Circle size={22} />}
                    </button>
                    {m.kind === "video" && (
                      <span className="tile-badge">
                        <Play size={11} fill="currentColor" /> {formatDuration(m.duration_ms)}
                      </span>
                    )}
                    {m.is_favourite && <Heart className="tile-fav" size={15} fill="currentColor" />}
                    {sort === "size" && <span className="tile-size">{formatBytes(m.size_bytes)}</span>}
                  </div>
                );
              })}
            </div>
          </section>
        ))
      )}
      <div ref={sentinel} className="sentinel">
        {q.isFetchingNextPage && <div className="spinner small" />}
      </div>

      {viewIndex !== null && items[viewIndex] && (
        <Viewer items={items} index={viewIndex} onIndex={setViewIndex} onClose={() => setViewIndex(null)} />
      )}
      {actions.modal}
    </div>
  );
}
