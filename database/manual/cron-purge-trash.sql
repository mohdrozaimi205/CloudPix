-- CloudPix — jadual harian untuk:
--   * padam kekal fail dalam Recently Deleted yang lebih 30 hari
--   * buang upload tergantung (pending > 2 hari)
--   * bersihkan kaunter rate limit lama
--
-- CARA GUNA (sekali sahaja, selepas Edge Functions di-deploy):
--   1. Supabase Dashboard → Database → Extensions → hidupkan "pg_cron" dan "pg_net".
--   2. Gantikan <PROJECT_REF> dan <CRON_SECRET> di bawah
--      (CRON_SECRET mesti SAMA dengan GitHub Secret CRON_SECRET).
--   3. Supabase Dashboard → SQL Editor → tampal → Run.
--
-- JANGAN commit fail ini selepas anda isi CRON_SECRET sebenar.

select cron.unschedule('cloudpix-purge-trash')
 where exists (select 1 from cron.job where jobname = 'cloudpix-purge-trash');

select cron.schedule(
  'cloudpix-purge-trash',
  '17 19 * * *',  -- 19:17 UTC = 3:17 pagi waktu Malaysia
  $$
  select net.http_post(
    url     := 'https://<PROJECT_REF>.supabase.co/functions/v1/purge-trash',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', '<CRON_SECRET>'),
    body    := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
