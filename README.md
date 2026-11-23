# CloudPix ☁️📸

Storan awan peribadi 1TB untuk foto & video — **Android (Kotlin + Jetpack Compose)** dan **Web (React + TypeScript + Vite)** berkongsi backend yang sama.

| Lapisan | Teknologi |
|---|---|
| Auth | Supabase Auth (email + kata laluan, reset, sesi) |
| Database | Supabase PostgreSQL + Row Level Security |
| API selamat | Supabase Edge Functions (Deno) |
| Storan fail | Cloudflare R2 (bucket peribadi, URL bertandatangan) |
| Web hosting | Cloudflare Pages |
| Android build | Gradle + GitHub Actions → Releases |

## Struktur

```
cloudpix/
├── android/          Fasa 4 — app Android
├── web/              Fasa 3 — web app
├── backend/
│   └── supabase/
│       ├── config.toml
│       └── functions/   Edge Functions (API)
├── database/
│   ├── migrations/      skema, RLS, fungsi (dijalankan automatik)
│   └── manual/          jadual cron harian (sekali sahaja)
├── documentation/
│   ├── SETUP-BM.md      ← MULA DI SINI (panduan langkah demi langkah)
│   ├── API.md
│   └── ARCHITECTURE.md
└── .github/workflows/   deploy automatik
```

## Status pembangunan

- [x] **Fasa 1** — struktur repo, skema database, RLS, kuota 1TB, trigger penggunaan storan
- [x] **Fasa 2** — Edge Functions: upload (single + multipart + resume), signed URL, padam/pulih, share link, export, pembersihan harian, rate limit, validasi jenis fail (magic bytes)
- [x] **Fasa 3** — Web App (dashboard, galeri, album, kegemaran, baru dipadam, storan, tetapan, upload multipart + resume, kongsi, realtime)
- [x] **Fasa 4** — Android App (Compose, bottom nav, viewer zoom & video, upload WorkManager, auto backup, kunci biometrik)
- [ ] Fasa 5 — Auto backup, sync, pengurusan media lanjutan
- [ ] Fasa 6 — Ujian, keselamatan, prestasi
- [ ] Fasa 7 — Deploy akhir & APK

## Prinsip keselamatan

- Kunci R2 dan `service_role` **hanya** dalam Supabase Secrets — tidak pernah dalam APK atau JavaScript web.
- Bucket R2 **peribadi**; semua akses melalui URL bertandatangan yang luput.
- RLS: setiap pengguna hanya nampak data sendiri. Klien tidak boleh cipta/ubah saiz media terus.
- Kuota disemak secara atomik di database; storan dikira dari saiz fail **sebenar** yang disahkan dalam R2.

🌐 Web: https://mohdrozaimi205.github.io/CloudPix/ · 📱 APK: tab Releases

Mula: baca [`documentation/SETUP-BM.md`](documentation/SETUP-BM.md).
