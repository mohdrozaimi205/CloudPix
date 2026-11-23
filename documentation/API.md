# CloudPix API

Base URL: `https://<PROJECT_REF>.supabase.co/functions/v1`

Semua endpoint (kecuali `share-public` & `purge-trash`) perlukan:

```
Authorization: Bearer <access_token pengguna dari Supabase Auth>
apikey: <publishable/anon key>
Content-Type: application/json
```

Ralat sentiasa berbentuk `{ "error": { "code": "...", "message": "..." } }`.

| Kod HTTP | `code` | Maksud |
|---|---|---|
| 400 | `BAD_INPUT`, `BAD_JSON` | input tidak sah |
| 401 | `UNAUTHENTICATED` | token tiada / tamat |
| 404 | `NOT_FOUND` | fail bukan milik anda atau tiada |
| 409 | `NOT_UPLOADED`, `PARTS_MISSING` | upload belum lengkap |
| 410 | `UPLOAD_EXPIRED` | sesi multipart tamat (7 hari) |
| 413 | `FILE_TOO_LARGE` | > 50 GB |
| 415 | `UNSUPPORTED_TYPE`, `CONTENT_MISMATCH` | jenis fail ditolak |
| 422 | `SIZE_MISMATCH` | saiz sebenar ≠ saiz diisytihar |
| 429 | `RATE_LIMITED` | terlalu banyak permintaan |
| 507 | `QUOTA_EXCEEDED` | storan 1TB penuh (`storage` disertakan) |

---

## Aliran upload

```
upload-init ──► mode "single"    ──► PUT fail ke uploadUrl ──► (PUT thumbnail) ──► upload-complete
            └─► mode "multipart" ──► upload-multipart sign ──► PUT setiap bahagian
                                     (putus? upload-multipart list → sambung bahagian yang belum)
                                 ──► (PUT thumbnail) ──► upload-multipart complete
```

Kemajuan upload = bait yang benar-benar dihantar oleh klien (XHR `upload.onprogress` / OkHttp), bukan anggaran.

### `POST /upload-init`

```json
{
  "filename": "IMG_2041.HEIC",
  "size": 3481022,
  "sha256": "64 aksara hex huruf kecil (pilihan, untuk kesan duplikat)",
  "albumId": "uuid (pilihan)",
  "width": 4032, "height": 3024, "durationMs": null,
  "takenAt": "2026-11-20T08:15:00Z",
  "source": "web | android | backup",
  "allowDuplicate": false
}
```

Respons (duplikat): `{ "duplicate": true, "existing": { "id", "filename", "created_at" } }`

Respons (single, ≤ 64 MB):
```json
{ "duplicate": false, "mode": "single", "mediaId": "...", "uploadUrl": "https://...", "thumbUploadUrl": "https://...",
  "contentType": "image/heic", "kind": "image", "expiresInSeconds": 3600,
  "storage": { "quotaBytes": 1099511627776, "usedBytes": 0, "availableBytes": 1099511627776, "usedPercent": 0, "warningLevel": 0 } }
```

Respons (multipart, > 64 MB): sama, tetapi `"mode": "multipart", "partSize": 16777216, "partCount": 120` (tiada `uploadUrl`).

Upload fail: `PUT uploadUrl` dengan header `Content-Type: <contentType>` dan badan = bait fail.
Thumbnail (pilihan, JPEG/WEBP ≤ 2 MB, cth 512px): `PUT thumbUploadUrl` dengan `Content-Type: image/jpeg`.

### `POST /upload-multipart`

| action | badan | respons |
|---|---|---|
| `sign` | `{ mediaId, partNumbers: [1..100 nombor] }` | `{ urls: { "1": "https://..." }, partSize, partCount }` |
| `list` | `{ mediaId }` | `{ parts: [{partNumber, size}], uploadedBytes, partSize, partCount }` |
| `complete` | `{ mediaId }` | `{ completed: true, media, storage }` |
| `abort` | `{ mediaId }` | `{ aborted: true }` |

Bahagian *n* = bait `[(n-1)*partSize, min(n*partSize, size))`. PUT tanpa header tambahan.

### `POST /upload-complete` — `{ mediaId }` → `{ completed: true, media, storage }`

Pelayan menyemak: fail wujud dalam R2, saiz sama, magic bytes sepadan. Barulah storan dikira.

---

## Akses fail

### `POST /media-url`
`{ "ids": ["uuid", ...], "variant": "thumb" | "original" | "download" }`
→ `{ "urls": { "<id>": { "url": "https://...", "expiresAt": "..." } }, "missing": [] }`

- `thumb`: maks 200 id, luput 6 jam. `url: null` jika tiada thumbnail.
- `original`: maks 50, luput 1 jam, menyokong HTTP Range (stream video terus ke `<video>` / ExoPlayer).
- `download`: seperti original + `Content-Disposition: attachment` dengan nama fail asal.

### `POST /media-delete`
`{ "ids": [...≤500], "action": "trash" | "restore" | "purge" }`

### `POST /share-create`
`{ "mediaId": "...", "expiresInHours": 24 }` → `{ id, token, url, expiresAt }`
Batal pautan: `update share_links set revoked = true` (terus melalui supabase-js, RLS benarkan).

### `GET /share-public?token=...` (awam)
→ `{ filename, kind, mimeType, sizeBytes, width, height, durationMs, url, thumbUrl, linkExpiresAt }`

### `POST /export-data`
`{ "page": 0, "includeDownloadUrls": true }` → semua metadata akaun; media 2000 setiap halaman (`hasMore`).

---

## Akses terus database (supabase-js / supabase-kt, dilindungi RLS)

| Operasi | Contoh |
|---|---|
| Senarai galeri (pagination) | `from('media').select('*').eq('status','ready').is('deleted_at',null).order('created_at',{ascending:false}).range(0,59)` |
| Cari nama fail | `.ilike('filename', '%cuti%')` |
| Tapis jenis / album / tarikh | `.eq('kind','video')`, `.eq('album_id',id)`, `.gte('created_at', from).lt('created_at', to)` |
| Susun | `created_at` (baru/lama), `size_bytes`, `filename` |
| Kegemaran | `update({ is_favourite: true }).eq('id', id)` |
| Nama semula | `update({ filename: 'baru.jpg' }).eq('id', id)` |
| Pindah album | `update({ album_id }).in('id', ids)` |
| Album | `from('albums').insert({ name })` / `update` / `delete` |
| Statistik dashboard | `rpc('get_storage_stats')` |
| Carta bulanan | `rpc('get_upload_stats_by_month', { p_months: 12 })` |
| Sejarah backup | `from('backup_history').insert({...})` |
| Sync masa nyata | `channel('media').on('postgres_changes', { event: '*', schema: 'public', table: 'media' }, cb)` |

Amaran storan: `used_percent` ≥ 80 / 90 / 95 → paparkan amaran; upload-init menolak dengan 507 bila penuh.
