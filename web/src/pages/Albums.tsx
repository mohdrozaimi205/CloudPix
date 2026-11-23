import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, FolderOpen, MoreVertical, Pencil, Plus, Trash2, UploadCloud } from "lucide-react";
import { Gallery } from "../components/Gallery";
import { PageHead, useUploadPicker } from "../components/Layout";
import { Thumb } from "../components/Thumb";
import { ConfirmModal, Empty, PromptModal, useToast } from "../components/ui";
import { useAlbums, useInvalidate, useMediaSample } from "../lib/queries";
import { supabase } from "../lib/supabase";
import type { Album } from "../lib/types";

export function AlbumsPage() {
  const albums = useAlbums();
  const invalidate = useInvalidate();
  const [creating, setCreating] = useState(false);
  return (
    <div className="page">
      <PageHead title="Album" sub={`${albums.data?.length ?? 0} album`}>
        <button className="btn primary" onClick={() => setCreating(true)}>
          <Plus size={18} /> Album baru
        </button>
      </PageHead>
      {albums.data?.length === 0 && (
        <Empty icon={<FolderOpen />} title="Belum ada album" text="Cipta album untuk susun foto & video anda." />
      )}
      <div className="album-grid">
        {albums.data?.map((a) => (
          <AlbumCard key={a.id} a={a} />
        ))}
      </div>
      {creating && (
        <PromptModal
          title="Album baru"
          label="Nama album"
          confirm="Cipta"
          onClose={() => setCreating(false)}
          onSubmit={async (name) => {
            const { error } = await supabase.from("albums").insert({ name });
            if (error) throw error;
            invalidate();
          }}
        />
      )}
    </div>
  );
}

function AlbumCard({ a }: { a: Album }) {
  const cover = useMediaSample(`cover-${a.id}-${a.cover_media_id}`, () => {
    const q = supabase.from("media").select("*").eq("status", "ready").is("deleted_at", null);
    return a.cover_media_id ? q.eq("id", a.cover_media_id).limit(1) : q.eq("album_id", a.id).order("created_at", { ascending: false }).limit(1);
  });
  const count = a.media?.[0]?.count ?? 0;
  return (
    <Link to={`/albums/${a.id}`} className="album-card">
      <div className="album-cover glass">{cover.data?.[0] ? <Thumb m={cover.data[0]} /> : <FolderOpen size={34} />}</div>
      <b>{a.name}</b>
      <small className="muted">{count} item</small>
    </Link>
  );
}

export function AlbumDetail() {
  const { id } = useParams();
  const albums = useAlbums();
  const nav = useNavigate();
  const toast = useToast();
  const invalidate = useInvalidate();
  const picker = useUploadPicker(id);
  const [menu, setMenu] = useState(false);
  const [rename, setRename] = useState(false);
  const [del, setDel] = useState(false);
  const album = albums.data?.find((a) => a.id === id);
  if (!id) return null;
  return (
    <div className="page">
      <PageHead title={album?.name ?? "Album"} sub={`${album?.media?.[0]?.count ?? 0} item`}>
        <button className="icon-btn" onClick={() => nav("/albums")} aria-label="Kembali">
          <ArrowLeft size={19} />
        </button>
        <button className="btn primary" onClick={picker.open}>
          <UploadCloud size={18} /> Upload ke album
        </button>
        <div className="menu-wrap">
          <button className="icon-btn" onClick={() => setMenu(!menu)} aria-label="Menu">
            <MoreVertical size={19} />
          </button>
          {menu && (
            <div className="menu glass" onClick={() => setMenu(false)}>
              <button onClick={() => setRename(true)}>
                <Pencil size={16} /> Tukar nama
              </button>
              <button className="danger" onClick={() => setDel(true)}>
                <Trash2 size={16} /> Padam album
              </button>
            </div>
          )}
        </div>
      </PageHead>
      {picker.input}
      <Gallery base={{ view: "album", albumId: id }} emptyText="Album ini kosong. Upload atau pindahkan foto ke sini." />
      {rename && album && (
        <PromptModal
          title="Tukar nama album"
          label="Nama album"
          initial={album.name}
          confirm="Simpan"
          onClose={() => setRename(false)}
          onSubmit={async (name) => {
            const { error } = await supabase.from("albums").update({ name }).eq("id", id);
            if (error) throw error;
            invalidate();
          }}
        />
      )}
      {del && (
        <ConfirmModal
          title="Padam album?"
          text="Album akan dipadam. Foto & video di dalamnya TIDAK dipadam — ia kekal dalam Foto/Video."
          confirm="Padam album"
          danger
          onClose={() => setDel(false)}
          onConfirm={async () => {
            const { error } = await supabase.from("albums").delete().eq("id", id);
            if (error) return toast(error.message, "err");
            invalidate();
            nav("/albums");
          }}
        />
      )}
    </div>
  );
}
