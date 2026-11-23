-- CloudPix — fungsi database (RPC)

-- ---------------------------------------------------------------
-- Statistik storan untuk dashboard (pengguna semasa sahaja)
-- ---------------------------------------------------------------
create or replace function public.get_storage_stats()
returns table (
  quota_bytes     bigint,
  used_bytes      bigint,
  available_bytes bigint,
  used_percent    numeric,
  photo_count     bigint,
  video_count     bigint,
  photo_bytes     bigint,
  video_bytes     bigint,
  trash_count     bigint,
  trash_bytes     bigint,
  favourite_count bigint
)
language sql stable security invoker set search_path = '' as $$
  with p as (
    select storage_quota_bytes as q, storage_used_bytes as u
      from public.profiles where id = (select auth.uid())
  ), m as (
    select
      count(*) filter (where kind = 'image' and deleted_at is null)                       as pc,
      count(*) filter (where kind = 'video' and deleted_at is null)                       as vc,
      coalesce(sum(size_bytes) filter (where kind = 'image' and deleted_at is null), 0)   as pb,
      coalesce(sum(size_bytes) filter (where kind = 'video' and deleted_at is null), 0)   as vb,
      count(*) filter (where deleted_at is not null)                                      as tc,
      coalesce(sum(size_bytes) filter (where deleted_at is not null), 0)                  as tb,
      count(*) filter (where is_favourite and deleted_at is null)                         as fc
    from public.media
    where user_id = (select auth.uid()) and status = 'ready'
  )
  select p.q, p.u, greatest(p.q - p.u, 0),
         round(p.u::numeric * 100 / nullif(p.q, 0), 2),
         m.pc, m.vc, m.pb::bigint, m.vb::bigint, m.tc, m.tb::bigint, m.fc
    from p cross join m;
$$;
grant execute on function public.get_storage_stats() to authenticated;

-- ---------------------------------------------------------------
-- Analitik: jumlah upload ikut bulan (untuk carta storan)
-- ---------------------------------------------------------------
create or replace function public.get_upload_stats_by_month(p_months integer default 12)
returns table (month date, kind public.media_kind, file_count bigint, total_bytes bigint)
language sql stable security invoker set search_path = '' as $$
  select date_trunc('month', created_at)::date, kind, count(*), coalesce(sum(size_bytes), 0)::bigint
    from public.media
   where user_id = (select auth.uid())
     and status = 'ready'
     and created_at >= date_trunc('month', now()) - make_interval(months => greatest(least(p_months, 60), 1) - 1)
   group by 1, 2
   order by 1, 2;
$$;
grant execute on function public.get_upload_stats_by_month(integer) to authenticated;

-- ---------------------------------------------------------------
-- Rate limit (dipanggil oleh Edge Functions sahaja)
-- Pulangkan true jika permintaan dibenarkan.
-- ---------------------------------------------------------------
create or replace function public.check_rate_limit(p_key text, p_bucket text, p_max integer, p_window_seconds integer)
returns boolean
language plpgsql security definer set search_path = '' as $$
declare
  w timestamptz := to_timestamp(floor(extract(epoch from now()) / p_window_seconds) * p_window_seconds);
  n integer;
begin
  insert into public.rate_limits (key, bucket, window_start, hits)
  values (p_key, p_bucket, w, 1)
  on conflict (key, bucket, window_start) do update set hits = public.rate_limits.hits + 1
  returning hits into n;
  return n <= p_max;
end $$;
revoke all on function public.check_rate_limit(text, text, integer, integer) from public, anon, authenticated;
grant execute on function public.check_rate_limit(text, text, integer, integer) to service_role;

-- ---------------------------------------------------------------
-- Cipta rekod media 'pending' dengan semakan kuota secara atomik.
-- Kuota = fail 'ready' + fail 'pending' (sedang upload) + fail baru.
-- Advisory lock elak dua upload serentak melepasi had 1TB.
-- ---------------------------------------------------------------
create or replace function public.create_pending_media(
  p_id uuid, p_user uuid, p_kind public.media_kind, p_filename text, p_mime text,
  p_size bigint, p_sha256 text, p_r2_key text, p_album uuid,
  p_width integer, p_height integer, p_duration_ms bigint, p_taken_at timestamptz, p_source text
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_quota bigint;
  v_committed bigint;
begin
  perform pg_advisory_xact_lock(hashtextextended(p_user::text, 42));

  select storage_quota_bytes into v_quota from public.profiles where id = p_user;
  if v_quota is null then
    insert into public.profiles (id) values (p_user) on conflict do nothing;
    select storage_quota_bytes into v_quota from public.profiles where id = p_user;
  end if;

  select coalesce(sum(size_bytes), 0) into v_committed
    from public.media
   where user_id = p_user and status in ('ready', 'pending');

  if v_committed + p_size > v_quota then
    return jsonb_build_object('ok', false, 'quota_bytes', v_quota, 'committed_bytes', v_committed);
  end if;

  if p_album is not null and not exists (
    select 1 from public.albums where id = p_album and user_id = p_user) then
    p_album := null;
  end if;

  insert into public.media (id, user_id, album_id, kind, status, filename, mime_type, size_bytes,
                            sha256, r2_key, width, height, duration_ms, taken_at, source)
  values (p_id, p_user, p_album, p_kind, 'pending', p_filename, p_mime, p_size,
          p_sha256, p_r2_key, p_width, p_height, p_duration_ms, p_taken_at, p_source);

  return jsonb_build_object('ok', true, 'quota_bytes', v_quota, 'committed_bytes', v_committed + p_size);
end $$;
revoke all on function public.create_pending_media(uuid, uuid, public.media_kind, text, text, bigint, text, text, uuid, integer, integer, bigint, timestamptz, text)
  from public, anon, authenticated;
grant execute on function public.create_pending_media(uuid, uuid, public.media_kind, text, text, bigint, text, text, uuid, integer, integer, bigint, timestamptz, text)
  to service_role;

-- ---------------------------------------------------------------
-- Kira semula penggunaan (untuk pembetulan jika perlu, service role sahaja)
-- ---------------------------------------------------------------
create or replace function public.recalculate_storage_used(p_user uuid)
returns bigint
language sql security definer set search_path = '' as $$
  update public.profiles p
     set storage_used_bytes = (
       select coalesce(sum(size_bytes), 0) from public.media
        where user_id = p_user and status = 'ready')
   where p.id = p_user
  returning storage_used_bytes;
$$;
revoke all on function public.recalculate_storage_used(uuid) from public, anon, authenticated;
grant execute on function public.recalculate_storage_used(uuid) to service_role;
