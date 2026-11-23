-- CloudPix — Row Level Security
-- Prinsip: setiap pengguna hanya nampak & ubah data sendiri.
-- Operasi yang melibatkan R2 atau kuota (cipta media, padam kekal, share)
-- hanya boleh dibuat melalui Edge Functions (service role).

alter table public.profiles        enable row level security;
alter table public.albums          enable row level security;
alter table public.media           enable row level security;
alter table public.upload_sessions enable row level security;
alter table public.share_links     enable row level security;
alter table public.backup_history  enable row level security;
alter table public.rate_limits     enable row level security;

-- Tarik semua hak default, kemudian beri yang perlu sahaja.
revoke all on public.profiles, public.albums, public.media, public.upload_sessions,
              public.share_links, public.backup_history, public.rate_limits
  from anon, authenticated;

-- ---------------- profiles ----------------
grant select on public.profiles to authenticated;
grant update (display_name) on public.profiles to authenticated;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- ---------------- albums ----------------
grant select, delete on public.albums to authenticated;
grant insert (name, cover_media_id) on public.albums to authenticated;
grant update (name, cover_media_id) on public.albums to authenticated;

drop policy if exists albums_select_own on public.albums;
create policy albums_select_own on public.albums
  for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists albums_insert_own on public.albums;
create policy albums_insert_own on public.albums
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and (cover_media_id is null or exists (
      select 1 from public.media m where m.id = cover_media_id and m.user_id = (select auth.uid())))
  );

drop policy if exists albums_update_own on public.albums;
create policy albums_update_own on public.albums
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (
    user_id = (select auth.uid())
    and (cover_media_id is null or exists (
      select 1 from public.media m where m.id = cover_media_id and m.user_id = (select auth.uid())))
  );

drop policy if exists albums_delete_own on public.albums;
create policy albums_delete_own on public.albums
  for delete to authenticated using (user_id = (select auth.uid()));

-- ---------------- media ----------------
-- Pengguna boleh baca media sendiri, dan ubah medan "selamat" sahaja:
-- nama fail, album, kegemaran, padam-ke-tong-sampah / pulih, masa diakses.
-- Tiada INSERT/DELETE terus — itu melalui Edge Functions supaya kuota & R2 kekal tepat.
grant select on public.media to authenticated;
grant update (filename, album_id, is_favourite, deleted_at, last_accessed_at, taken_at)
  on public.media to authenticated;

drop policy if exists media_select_own on public.media;
create policy media_select_own on public.media
  for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists media_update_own on public.media;
create policy media_update_own on public.media
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (
    user_id = (select auth.uid())
    and (album_id is null or exists (
      select 1 from public.albums a where a.id = album_id and a.user_id = (select auth.uid())))
  );

-- ---------------- share_links ----------------
grant select, delete on public.share_links to authenticated;
grant update (revoked) on public.share_links to authenticated;

drop policy if exists share_select_own on public.share_links;
create policy share_select_own on public.share_links
  for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists share_update_own on public.share_links;
create policy share_update_own on public.share_links
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

drop policy if exists share_delete_own on public.share_links;
create policy share_delete_own on public.share_links
  for delete to authenticated using (user_id = (select auth.uid()));

-- ---------------- backup_history ----------------
grant select on public.backup_history to authenticated;
grant insert (device_name, status, files_uploaded, files_skipped, files_failed, bytes_uploaded, message, started_at, finished_at)
  on public.backup_history to authenticated;
grant update (status, files_uploaded, files_skipped, files_failed, bytes_uploaded, message, finished_at)
  on public.backup_history to authenticated;

drop policy if exists backup_select_own on public.backup_history;
create policy backup_select_own on public.backup_history
  for select to authenticated using (user_id = (select auth.uid()));

drop policy if exists backup_insert_own on public.backup_history;
create policy backup_insert_own on public.backup_history
  for insert to authenticated with check (user_id = (select auth.uid()));

drop policy if exists backup_update_own on public.backup_history;
create policy backup_update_own on public.backup_history
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- upload_sessions & rate_limits: RLS aktif tanpa polisi = tiada akses untuk klien.
