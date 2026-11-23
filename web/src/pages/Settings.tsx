import { useEffect, useState } from "react";
import { Download, KeyRound, Link2Off, LogOut, Moon, Server, Sun, User } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { PageHead } from "../components/Layout";
import { useToast } from "../components/ui";
import { api } from "../lib/api";
import { formatDateTime } from "../lib/format";
import { clearServerConfig, config, supabase } from "../lib/supabase";
import { getTheme, setTheme, type Theme } from "../lib/theme";

export function SettingsPage({ email }: { email: string }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [theme, setThemeState] = useState<Theme>(getTheme());
  const [name, setName] = useState("");
  const [pw, setPw] = useState("");
  const [exporting, setExporting] = useState<string | null>(null);

  const profile = useQuery({
    queryKey: ["profile"],
    queryFn: async () => (await supabase.from("profiles").select("display_name").single()).data,
  });
  useEffect(() => setName(profile.data?.display_name ?? ""), [profile.data]);

  const shares = useQuery({
    queryKey: ["shares"],
    queryFn: async () => {
      const { data, error } = await supabase
        .from("share_links")
        .select("id, expires_at, revoked, view_count, created_at, media(filename)")
        .order("created_at", { ascending: false })
        .limit(100);
      if (error) throw error;
      return data as unknown as { id: string; expires_at: string; revoked: boolean; view_count: number; created_at: string; media: { filename: string } | null }[];
    },
  });

  const exportAll = async () => {
    try {
      const all: Record<string, unknown> = {};
      const media: unknown[] = [];
      for (let page = 0; ; page++) {
        setExporting(`Mengumpul halaman ${page + 1}…`);
        const r = await api.exportData(page);
        if (page === 0) Object.assign(all, r);
        media.push(...r.media);
        if (!r.hasMore) break;
      }
      all.media = media;
      delete all.page;
      delete all.hasMore;
      const blob = new Blob([JSON.stringify(all, null, 2)], { type: "application/json" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = `cloudpix-export-${new Date().toISOString().slice(0, 10)}.json`;
      a.click();
      URL.revokeObjectURL(a.href);
      toast("Eksport siap. Pautan muat turun dalam fail sah 24 jam.", "ok");
    } catch (e) {
      toast((e as Error).message, "err");
    } finally {
      setExporting(null);
    }
  };

  return (
    <div className="page narrow">
      <PageHead title="Tetapan" />

      <section className="card glass stack">
        <h3 className="row">
          <User size={18} /> Akaun
        </h3>
        <p className="muted small">{email}</p>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            const { error } = await supabase.from("profiles").update({ display_name: name.trim() || null }).eq("id", (await supabase.auth.getUser()).data.user!.id);
            toast(error ? error.message : "Nama disimpan", error ? "err" : "ok");
            qc.invalidateQueries({ queryKey: ["profile"] });
          }}
        >
          <input className="grow" placeholder="Nama paparan" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
          <button className="btn ghost">Simpan</button>
        </form>
        <form
          className="row"
          onSubmit={async (e) => {
            e.preventDefault();
            if (pw.length < 8) return toast("Minimum 8 aksara", "err");
            const { error } = await supabase.auth.updateUser({ password: pw });
            toast(error ? error.message : "Kata laluan ditukar", error ? "err" : "ok");
            if (!error) setPw("");
          }}
        >
          <input className="grow" type="password" placeholder="Kata laluan baru" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password" />
          <button className="btn ghost">
            <KeyRound size={16} /> Tukar
          </button>
        </form>
      </section>

      <section className="card glass stack">
        <h3 className="row">{theme === "dark" ? <Moon size={18} /> : <Sun size={18} />} Tema</h3>
        <div className="seg">
          {(["system", "light", "dark"] as Theme[]).map((t) => (
            <button key={t} className={theme === t ? "on" : ""} onClick={() => (setTheme(t), setThemeState(t))}>
              {t === "system" ? "Ikut sistem" : t === "light" ? "Cerah" : "Gelap"}
            </button>
          ))}
        </div>
      </section>

      <section className="card glass stack">
        <h3 className="row">
          <Link2Off size={18} /> Pautan kongsi
        </h3>
        {!shares.data?.length && <p className="muted small">Tiada pautan kongsi.</p>}
        <div className="list">
          {shares.data?.map((s) => {
            const expired = new Date(s.expires_at) < new Date();
            return (
              <div key={s.id} className="list-item static">
                <div className="stack tight grow">
                  <b className="ellipsis">{s.media?.filename ?? "(fail dipadam)"}</b>
                  <small className="muted">
                    {s.revoked ? "Dibatalkan" : expired ? "Luput" : `Luput ${formatDateTime(s.expires_at)}`} · {s.view_count} tontonan
                  </small>
                </div>
                {!s.revoked && !expired && (
                  <button
                    className="btn ghost small"
                    onClick={async () => {
                      await supabase.from("share_links").update({ revoked: true }).eq("id", s.id);
                      shares.refetch();
                      toast("Pautan dibatalkan", "ok");
                    }}
                  >
                    Batalkan
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </section>

      <section className="card glass stack">
        <h3 className="row">
          <Download size={18} /> Eksport data akaun
        </h3>
        <p className="muted small">Muat turun fail JSON berisi semua metadata, album, sejarah backup & pautan muat turun fail asal.</p>
        <button className="btn ghost" onClick={exportAll} disabled={!!exporting}>
          {exporting ?? "Eksport sekarang"}
        </button>
      </section>

      <section className="card glass stack">
        <h3 className="row">
          <Server size={18} /> Server
        </h3>
        <p className="muted small ellipsis">{config?.url}</p>
        <div className="row">
          <button className="btn ghost" onClick={() => (clearServerConfig(), supabase.auth.signOut().finally(() => location.reload()))}>
            Tukar server
          </button>
          <button className="btn danger" onClick={() => supabase.auth.signOut()}>
            <LogOut size={16} /> Log keluar
          </button>
        </div>
      </section>
    </div>
  );
}
