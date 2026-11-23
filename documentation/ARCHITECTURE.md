# Seni bina CloudPix

```
 ┌──────────────┐        ┌──────────────┐
 │ Android app  │        │   Web app    │   (anon key + token pengguna sahaja)
 └──────┬───────┘        └──────┬───────┘
        │ supabase-kt           │ supabase-js
        ▼                       ▼
 ┌─────────────────────────────────────────┐
 │ Supabase                                 │
 │  Auth ─ PostgreSQL (RLS) ─ Realtime      │◄── perubahan media/album dihantar
 │  Edge Functions (service role, kunci R2) │    serta-merta ke semua peranti
 └───────────────┬─────────────────────────┘
                 │ SigV4 (pelayan sahaja)
                 ▼
 ┌─────────────────────────────────────────┐
 │ Cloudflare R2 (bucket peribadi)          │
 │  u/<userId>/o/<mediaId>.<ext>  asal      │
 │  u/<userId>/t/<mediaId>.jpg    thumbnail │
 └─────────────────────────────────────────┘
        ▲
        │ PUT/GET terus guna URL bertandatangan (luput)
   Android / Web
```

## Kenapa begini

- **Fail besar tidak lalu Edge Functions** — klien upload terus ke R2 dengan URL bertandatangan, jadi tiada had masa fungsi dan kelajuan penuh.
- **Kuota tepat** — `create_pending_media` kunci per-pengguna (advisory lock), kira `ready + pending + fail baru ≤ 1TB`. Trigger `media_usage` kemas kini `storage_used_bytes` hanya bila fail disahkan (`ready`) dan bila dipadam kekal.
- **Tiada kejayaan palsu** — status `ready` hanya selepas pelayan sahkan objek dalam R2 (HEAD + saiz + magic bytes).
- **Resume** — sesi multipart disimpan 7 hari; `list` beritahu bahagian mana sudah diterima R2.
- **Sync** — semua klien langgan Realtime pada `media`, `albums`, `profiles`. Upload di Android → baris `media` jadi `ready` → web terima event → galeri dikemas kini.
- **Recently Deleted** — `deleted_at` diisi; cron harian padam kekal selepas 30 hari.
- **Duplikat** — klien kira SHA-256 sebelum upload; pelayan tolak jika hash sama sudah wujud.
