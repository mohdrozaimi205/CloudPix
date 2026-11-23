import { useState, type ReactNode } from "react";
import { Copy, FolderPlus } from "lucide-react";
import { api } from "../lib/api";
import { useAlbums, useInvalidate } from "../lib/queries";
import { supabase } from "../lib/supabase";
import type { Media } from "../lib/types";
import { downloadMedia } from "../lib/urls";
import { ConfirmModal, Modal, PromptModal, useToast } from "./ui";

/** Semua tindakan media + modal yang berkaitan, dikongsi oleh galeri & viewer. */
export function useMediaActions() {
  const toast = useToast();
  const invalidate = useInvalidate();
  const [modal, setModal] = useState<ReactNode>(null);
  const close = () => setModal(null);

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    try {
      await fn();
      if (ok) toast(ok, "ok");
      invalidate();
    } catch (e) {
      toast((e as Error).message || "Ralat", "err");
      throw e;
    }
  };

  const update = (ids: string[], values: Record<string, unknown>) =>
    run(async () => {
      const { error } = await supabase.from("media").update(values).in("id", ids);
      if (error) throw error;
    });

  return {
    modal,
    favourite: (items: Media[], value?: boolean) => {
      const v = value ?? !items.every((m) => m.is_favourite);
      return update(items.map((m) => m.id), { is_favourite: v }).then(() =>
        toast(v ? "Ditambah ke Kegemaran" : "Dibuang dari Kegemaran", "ok"),
      );
    },
    rename: (m: Media) =>
      setModal(
        <PromptModal
          title="Tukar nama"
          label="Nama fail"
          initial={m.filename}
          confirm="Simpan"
          onClose={close}
          onSubmit={async (name) => {
            const ext = m.filename.includes(".") ? m.filename.slice(m.filename.lastIndexOf(".")) : "";
            const finalName = name.includes(".") ? name : name + ext;
            await update([m.id], { filename: finalName });
            toast("Nama ditukar", "ok");
          }}
        />,
      ),
    move: (items: Media[]) => setModal(<MoveModal items={items} onClose={close} onDone={invalidate} />),
    download: (items: Media[]) =>
      run(() => downloadMedia(items.map((m) => m.id)), items.length > 1 ? `Memuat turun ${items.length} fail…` : undefined),
    trash: (items: Media[]) =>
      run(() => api.mediaDelete(items.map((m) => m.id), "trash"), `${items.length} fail dipindah ke Baru Dipadam`),
    restore: (items: Media[]) => run(() => api.mediaDelete(items.map((m) => m.id), "restore"), `${items.length} fail dipulihkan`),
    purge: (items: Media[]) =>
      setModal(
        <ConfirmModal
          title="Padam kekal?"
          text={`${items.length} fail akan dipadam selama-lamanya dan ruang storan dibebaskan. Tindakan ini tidak boleh dibatalkan.`}
          confirm="Padam kekal"
          danger
          onClose={close}
          onConfirm={() => run(() => api.mediaDelete(items.map((m) => m.id), "purge"), "Dipadam kekal")}
        />,
      ),
    share: (m: Media) => setModal(<ShareModal m={m} onClose={close} />),
  };
}

function MoveModal({ items, onClose, onDone }: { items: Media[]; onClose: () => void; onDone: () => void }) {
  const albums = useAlbums();
  const toast = useToast();
  const [creating, setCreating] = useState(false);
  const move = async (albumId: string | null) => {
    const { error } = await supabase.from("media").update({ album_id: albumId }).in("id", items.map((m) => m.id));
    if (error) return toast(error.message, "err");
    toast(albumId ? "Dipindah ke album" : "Dibuang dari album", "ok");
    onDone();
    onClose();
  };
  if (creating)
    return (
      <PromptModal
        title="Album baru"
        label="Nama album"
        confirm="Cipta & pindah"
        onClose={onClose}
        onSubmit={async (name) => {
          const { data, error } = await supabase.from("albums").insert({ name }).select("id").single();
          if (error) throw error;
          await move(data.id);
        }}
      />
    );
  return (
    <Modal title={`Pindah ${items.length} fail ke album`} onClose={onClose}>
      <div className="list">
        <button className="list-item" onClick={() => setCreating(true)}>
          <FolderPlus size={18} /> Album baru…
        </button>
        {albums.data?.map((a) => (
          <button key={a.id} className="list-item" onClick={() => move(a.id)}>
            {a.name}
            <span className="muted small">{a.media?.[0]?.count ?? 0}</span>
          </button>
        ))}
        {items.some((m) => m.album_id) && (
          <button className="list-item muted" onClick={() => move(null)}>
            Buang dari album
          </button>
        )}
      </div>
    </Modal>
  );
}

function ShareModal({ m, onClose }: { m: Media; onClose: () => void }) {
  const toast = useToast();
  const [hours, setHours] = useState(24);
  const [link, setLink] = useState<{ url: string | null; expiresAt: string } | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <Modal title="Kongsi pautan selamat" onClose={onClose}>
      <p className="muted small">
        Sesiapa yang ada pautan boleh lihat <b>{m.filename}</b> sehingga pautan luput. Anda boleh batalkan di Tetapan.
      </p>
      {!link ? (
        <div className="stack">
          <label className="field">
            <span>Tempoh sah</span>
            <select value={hours} onChange={(e) => setHours(Number(e.target.value))}>
              <option value={1}>1 jam</option>
              <option value={24}>24 jam</option>
              <option value={168}>7 hari</option>
              <option value={720}>30 hari</option>
            </select>
          </label>
          <button
            className="btn primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                setLink(await api.shareCreate(m.id, hours));
              } catch (e) {
                toast((e as Error).message, "err");
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Mencipta…" : "Cipta pautan"}
          </button>
        </div>
      ) : (
        <div className="stack">
          <div className="copy-row">
            <input readOnly value={link.url ?? "(PUBLIC_WEB_URL belum diset di server)"} onFocus={(e) => e.target.select()} />
            <button
              className="btn primary"
              onClick={() => link.url && navigator.clipboard.writeText(link.url).then(() => toast("Pautan disalin", "ok"))}
            >
              <Copy size={16} /> Salin
            </button>
          </div>
          <p className="muted small">Luput: {new Date(link.expiresAt).toLocaleString("ms-MY")}</p>
        </div>
      )}
    </Modal>
  );
}
