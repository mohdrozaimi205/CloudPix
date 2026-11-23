import { useEffect, useState } from "react";
import { HashRouter, Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { Session } from "@supabase/supabase-js";
import { useQueryClient } from "@tanstack/react-query";
import { Gallery } from "./components/Gallery";
import { Layout, PageHead } from "./components/Layout";
import { config, supabase } from "./lib/supabase";
import { setUploadFinishedHandler } from "./lib/uploads";
import { AlbumDetail, AlbumsPage } from "./pages/Albums";
import { AuthPage, NewPasswordPage, SetupPage } from "./pages/Auth";
import { Dashboard } from "./pages/Dashboard";
import { SettingsPage } from "./pages/Settings";
import { SharePublic } from "./pages/SharePublic";
import { StoragePage } from "./pages/Storage";

function Page({ title, sub, children }: { title: string; sub?: string; children: React.ReactNode }) {
  return (
    <div className="page">
      <PageHead title={title} sub={sub} />
      {children}
    </div>
  );
}

function useRealtimeSync(userId: string | undefined) {
  const qc = useQueryClient();
  useEffect(() => {
    if (!userId) return;
    let t: number | undefined;
    const refresh = (keys: string[][]) => {
      clearTimeout(t);
      t = window.setTimeout(() => keys.forEach((k) => qc.invalidateQueries({ queryKey: k })), 400);
    };
    setUploadFinishedHandler(() => refresh([["media"], ["stats"], ["albums"]]));
    // Perubahan dari Android / tab lain → kemas kini galeri serta-merta
    const ch = supabase
      .channel(`sync-${userId}`)
      .on("postgres_changes", { event: "*", schema: "public", table: "media", filter: `user_id=eq.${userId}` }, () =>
        refresh([["media"], ["stats"], ["albums"]]),
      )
      .on("postgres_changes", { event: "*", schema: "public", table: "albums", filter: `user_id=eq.${userId}` }, () =>
        refresh([["albums"]]),
      )
      .on("postgres_changes", { event: "*", schema: "public", table: "profiles", filter: `id=eq.${userId}` }, () =>
        refresh([["stats"], ["profile"]]),
      )
      .subscribe();
    const onFocus = () => refresh([["media"], ["stats"]]);
    window.addEventListener("focus", onFocus);
    return () => {
      supabase.removeChannel(ch);
      window.removeEventListener("focus", onFocus);
    };
  }, [userId, qc]);
}

function Shell() {
  const loc = useLocation();
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const [recovery, setRecovery] = useState(false);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data } = supabase.auth.onAuthStateChange((event, s) => {
      if (event === "PASSWORD_RECOVERY") setRecovery(true);
      setSession(s);
    });
    return () => data.subscription.unsubscribe();
  }, []);
  useRealtimeSync(session?.user.id);

  if (loc.pathname.startsWith("/s/")) return <SharePublic />;
  if (!config) return <SetupPage />;
  if (session === undefined) return <div className="boot"><div className="spinner" /></div>;
  if (recovery && session) return <NewPasswordPage onDone={() => setRecovery(false)} />;
  if (!session) return <AuthPage />;

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/photos" element={<Page title="Foto"><Gallery base={{ view: "photos" }} showKindFilter={false} emptyText="Upload foto pertama anda." /></Page>} />
        <Route path="/videos" element={<Page title="Video"><Gallery base={{ view: "videos" }} showKindFilter={false} emptyText="Upload video pertama anda." /></Page>} />
        <Route path="/favourites" element={<Page title="Kegemaran"><Gallery base={{ view: "favourites" }} emptyText="Tekan ♥ pada foto atau video untuk simpan di sini." /></Page>} />
        <Route path="/trash" element={<Page title="Baru Dipadam" sub="Fail dipadam kekal secara automatik selepas 30 hari"><Gallery base={{ view: "trash" }} emptyText="Tiada fail dipadam." /></Page>} />
        <Route path="/albums" element={<AlbumsPage />} />
        <Route path="/albums/:id" element={<AlbumDetail />} />
        <Route path="/storage" element={<StoragePage />} />
        <Route path="/settings" element={<SettingsPage email={session.user.email ?? ""} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  );
}

export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/s/:token" element={<SharePublic />} />
        <Route path="*" element={<Shell />} />
      </Routes>
    </HashRouter>
  );
}
