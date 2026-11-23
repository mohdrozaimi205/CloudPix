-- CloudPix — skema utama
-- Fail media sebenar disimpan dalam Cloudflare R2. Jadual di sini hanya simpan metadata.

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

-- ---------------------------------------------------------------
-- Enum
-- ---------------------------------------------------------------
do $$ begin
  create type public.media_kind as enum ('image', 'video');
exception when duplicate_object then null; end $$;

do $$ begin
  create type public.media_status as enum ('pending', 'ready', 'failed');
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------
-- profiles: satu baris setiap pengguna, simpan kuota & penggunaan
-- ---------------------------------------------------------------
create table if not exists public.profiles (
  id                  uuid primary key references auth.users (id) on delete cascade,
  display_name        text check (display_name is null or char_length(display_name) <= 80),
  storage_quota_bytes bigint not null default 1099511627776,  -- 1 TB = 1024 GB
  storage_used_bytes  bigint not null default 0 check (storage_used_bytes >= 0),
  created_at          timestamptz not null default now(),
  updated_at          timestamptz not null default now()
);

-- ---------------------------------------------------------------
-- albums
-- ---------------------------------------------------------------
create table if not exists public.albums (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name           text not null check (char_length(btrim(name)) between 1 and 120),
  cover_media_id uuid,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index if not exists albums_user_idx on public.albums (user_id, created_at desc);

-- ---------------------------------------------------------------
-- media: metadata setiap foto / video
-- ---------------------------------------------------------------
create table if not exists public.media (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users (id) on delete cascade,
  album_id         uuid references public.albums (id) on delete set null,
  kind             public.media_kind not null,
  status           public.media_status not null default 'pending',
  filename         text not null check (char_length(filename) between 1 and 255),
  mime_type        text not null,
  size_bytes       bigint not null check (size_bytes > 0),
  sha256           text check (sha256 is null or sha256 ~ '^[0-9a-f]{64}$'),
  r2_key           text not null unique,
  thumb_key        text,
  width            integer check (width is null or width > 0),
  height           integer check (height is null or height > 0),
  duration_ms      bigint check (duration_ms is null or duration_ms >= 0),
  is_favourite     boolean not null default false,
  taken_at         timestamptz,
  last_accessed_at timestamptz,
  deleted_at       timestamptz,          -- bukan null = dalam "Recently Deleted"
  source           text not null default 'web' check (source in ('web', 'android', 'backup')),
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);

create index if not exists media_user_created_idx on public.media (user_id, created_at desc, id desc)
  where deleted_at is null and status = 'ready';
create index if not exists media_user_kind_idx on public.media (user_id, kind, created_at desc)
  where deleted_at is null and status = 'ready';
create index if not exists media_user_album_idx on public.media (user_id, album_id, created_at desc);
create index if not exists media_user_fav_idx on public.media (user_id, created_at desc) where is_favourite;
create index if not exists media_user_deleted_idx on public.media (user_id, deleted_at) where deleted_at is not null;
create index if not exists media_user_sha_idx on public.media (user_id, sha256) where sha256 is not null;
create index if not exists media_user_size_idx on public.media (user_id, size_bytes desc);
create index if not exists media_filename_trgm_idx on public.media using gin (filename gin_trgm_ops);
create index if not exists media_pending_idx on public.media (created_at) where status <> 'ready';

do $$ begin
  alter table public.albums
    add constraint albums_cover_fk foreign key (cover_media_id) references public.media (id) on delete set null;
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------
-- upload_sessions: multipart upload yang sedang berjalan (untuk resume)
-- Hanya service role (Edge Functions) yang boleh akses.
-- ---------------------------------------------------------------
create table if not exists public.upload_sessions (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users (id) on delete cascade,
  media_id     uuid not null unique references public.media (id) on delete cascade,
  r2_key       text not null,
  r2_upload_id text not null,
  part_size    bigint not null,
  part_count   integer not null check (part_count between 1 and 10000),
  created_at   timestamptz not null default now(),
  expires_at   timestamptz not null default now() + interval '7 days'
);

-- ---------------------------------------------------------------
-- share_links: pautan kongsi selamat dengan tarikh luput
-- ---------------------------------------------------------------
create table if not exists public.share_links (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users (id) on delete cascade,
  media_id    uuid not null references public.media (id) on delete cascade,
  token       text not null unique default encode(gen_random_bytes(24), 'hex'),
  expires_at  timestamptz not null,
  revoked     boolean not null default false,
  view_count  integer not null default 0,
  created_at  timestamptz not null default now()
);
create index if not exists share_links_user_idx on public.share_links (user_id, created_at desc);

-- ---------------------------------------------------------------
-- backup_history: rekod sesi auto-backup Android
-- ---------------------------------------------------------------
create table if not exists public.backup_history (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users (id) on delete cascade,
  device_name    text not null check (char_length(device_name) <= 100),
  status         text not null default 'running' check (status in ('running', 'completed', 'paused', 'failed')),
  files_uploaded integer not null default 0 check (files_uploaded >= 0),
  files_skipped  integer not null default 0 check (files_skipped >= 0),
  files_failed   integer not null default 0 check (files_failed >= 0),
  bytes_uploaded bigint not null default 0 check (bytes_uploaded >= 0),
  message        text check (message is null or char_length(message) <= 500),
  started_at     timestamptz not null default now(),
  finished_at    timestamptz
);
create index if not exists backup_history_user_idx on public.backup_history (user_id, started_at desc);

-- ---------------------------------------------------------------
-- rate_limits: kaunter tetingkap masa (key = user id atau ip)
-- ---------------------------------------------------------------
create table if not exists public.rate_limits (
  key          text not null,
  bucket       text not null,
  window_start timestamptz not null,
  hits         integer not null default 0,
  primary key (key, bucket, window_start)
);

-- ---------------------------------------------------------------
-- Trigger: updated_at
-- ---------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end $$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
  for each row execute function public.touch_updated_at();
drop trigger if exists albums_touch on public.albums;
create trigger albums_touch before update on public.albums
  for each row execute function public.touch_updated_at();
drop trigger if exists media_touch on public.media;
create trigger media_touch before update on public.media
  for each row execute function public.touch_updated_at();

-- ---------------------------------------------------------------
-- Trigger: buat profile bila pengguna baru daftar
-- ---------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, nullif(left(coalesce(new.raw_user_meta_data ->> 'display_name', ''), 80), ''))
  on conflict (id) do nothing;
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------
-- Trigger: kemas kini storage_used_bytes ikut saiz fail SEBENAR.
-- Hanya fail berstatus 'ready' dikira. Fail dalam Recently Deleted masih
-- dikira (masih wujud dalam R2) sehingga dipadam kekal.
-- ---------------------------------------------------------------
create or replace function public.media_usage_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  old_bytes bigint := 0;
  new_bytes bigint := 0;
  uid uuid;
begin
  if tg_op in ('UPDATE', 'DELETE') and old.status = 'ready' then old_bytes := old.size_bytes; end if;
  if tg_op in ('INSERT', 'UPDATE') and new.status = 'ready' then new_bytes := new.size_bytes; end if;
  uid := case when tg_op = 'DELETE' then old.user_id else new.user_id end;
  if new_bytes <> old_bytes then
    update public.profiles
       set storage_used_bytes = greatest(0, storage_used_bytes + new_bytes - old_bytes)
     where id = uid;
  end if;
  return null;
end $$;

drop trigger if exists media_usage on public.media;
create trigger media_usage after insert or update of status, size_bytes or delete on public.media
  for each row execute function public.media_usage_trigger();

-- ---------------------------------------------------------------
-- Realtime: Android & Web dapat perubahan serta-merta
-- ---------------------------------------------------------------
do $$ begin
  alter publication supabase_realtime add table public.media;
exception when duplicate_object then null; when undefined_object then null; end $$;
do $$ begin
  alter publication supabase_realtime add table public.albums;
exception when duplicate_object then null; when undefined_object then null; end $$;
do $$ begin
  alter publication supabase_realtime add table public.profiles;
exception when duplicate_object then null; when undefined_object then null; end $$;
