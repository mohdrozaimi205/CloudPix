# Panduan Setup CloudPix (tanpa terminal)

Semua langkah di bawah dibuat melalui laman web (Supabase, Cloudflare, GitHub). Ikut satu per satu.

> 💰 **Kos:** Supabase pelan percuma cukup untuk mula. Cloudflare R2 percuma untuk 10 GB pertama; selepas itu USD 0.015 setiap GB sebulan. Jika 1 TB penuh ≈ **USD 15 sebulan** (lebih kurang RM 65–70). Tiada caj muat turun (egress). R2 perlukan kad bank/kredit untuk diaktifkan walaupun guna bahagian percuma.

---

## Langkah 1 — Supabase (database + login)

1. Pergi ke **supabase.com** → Sign in (boleh guna akaun GitHub).
2. **New project** → nama `cloudpix` → buat kata laluan database yang kuat → **simpan kata laluan ini** → Region: **Singapore** → Create.
3. Tunggu ±2 minit sehingga projek siap.
4. Salin maklumat ini ke Notepad (JANGAN kongsi dengan sesiapa):

| Apa | Di mana |
|---|---|
| **Project Ref** | Project Settings → General → *Reference ID* |
| **Project URL** | Project Settings → API Keys / Data API → `https://xxxx.supabase.co` |
| **Publishable (anon) key** | Project Settings → API Keys → *Publishable key* |
| **DB URL** | Butang **Connect** (atas) → *Session pooler* → salin URI → gantikan `[YOUR-PASSWORD]` dengan kata laluan langkah 2 |
| **Access Token** | Ikon profil (kiri bawah) → Account preferences → **Access Tokens** → Generate new token |

> ⚠️ Jika kata laluan database ada simbol seperti `@ # / ?`, tukar kata laluan kepada huruf + nombor sahaja (Project Settings → Database → Reset password) supaya URL tidak rosak.

5. **Authentication → Sign In / Providers → Email**: pastikan *Email* hidup dan *Confirm email* hidup.
6. **Authentication → URL Configuration**: Site URL = `https://mohdrozaimi205.github.io/CloudPix/` dan tambah yang sama dalam *Redirect URLs*.

## Langkah 2 — Cloudflare R2 (tempat simpan foto & video)

1. Pergi ke **dash.cloudflare.com** → daftar/log masuk.
2. Menu kiri → **R2 Object Storage** → aktifkan (masukkan kad).
3. **Create bucket** → nama `cloudpix-media` → Location: *Automatic* → Create.
   - Jangan hidupkan *Public access*. Bucket mesti kekal peribadi.
4. Dalam bucket → **Settings** → **CORS Policy** → Add → tampal:

```json
[
  {
    "AllowedOrigins": ["https://mohdrozaimi205.github.io", "https://cloudpix.pages.dev", "http://localhost:5173"],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag", "Content-Length", "Content-Range"],
    "MaxAgeSeconds": 3600
  }
]
```

5. Masih dalam **Settings** → **Object lifecycle rules** → Add rule → *Abort incomplete multipart uploads* selepas **7 hari** (elak bayar untuk upload yang terbengkalai).
6. Kembali ke halaman **R2 Overview** → salin **Account ID** (sebelah kanan).
7. **Manage R2 API Tokens** (atau *API Tokens*) → **Create API token**:
   - Permissions: **Object Read & Write**
   - Specify bucket: **cloudpix-media** sahaja
   - Create → salin **Access Key ID** dan **Secret Access Key** (hanya dipaparkan sekali!).

## Langkah 3 — GitHub (simpan kod & deploy automatik)

1. **github.com/new** → Repository name: `CloudPix` → **Private** → Create repository.
2. Dalam repo baru → **uploading an existing file** → seret SEMUA isi folder `cloudpix` (termasuk folder `.github`) → **Commit changes**.
   - Jika folder `.github` tidak ikut naik (Windows sembunyikan folder bermula titik), buka repo → **Add file → Create new file** → taip nama `.github/workflows/deploy-backend.yml` → tampal isi fail itu → Commit. Ulang untuk `secret-scan.yml`.
3. Repo → **Settings → Secrets and variables → Actions → New repository secret**. Masukkan satu per satu:

| Name | Value |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | Access Token (Langkah 1) |
| `SUPABASE_PROJECT_REF` | Project Ref |
| `SUPABASE_DB_URL` | DB URL penuh dengan kata laluan |
| `R2_ACCOUNT_ID` | Account ID R2 |
| `R2_ACCESS_KEY_ID` | Access Key ID |
| `R2_SECRET_ACCESS_KEY` | Secret Access Key |
| `R2_BUCKET` | `cloudpix-media` |
| `CRON_SECRET` | rentetan rawak ≥ 32 aksara (cth jana di 1password.com/password-generator) |

4. Tab **Variables** (sebelah Secrets) → New repository variable:

| Name | Value |
|---|---|
| `PUBLIC_WEB_URL` | `https://mohdrozaimi205.github.io/CloudPix` |
| `ALLOWED_ORIGINS` | `https://mohdrozaimi205.github.io,https://cloudpix.pages.dev,http://localhost:5173` |
| `VITE_SUPABASE_URL` | Project URL (`https://xxxx.supabase.co`) |
| `VITE_SUPABASE_ANON_KEY` | Publishable key |

> `VITE_SUPABASE_*` adalah kunci **awam** — ia dibina ke dalam web & APK supaya pengguna tidak perlu taip. Jika tidak diisi, app akan minta di skrin "Sambung Server".

5. Tab **Actions** → **Deploy Backend (Supabase)** → **Run workflow**. Tunggu tanda ✅ hijau.
   - Jika ❌ merah: klik pada larian itu → salin mesej ralat → hantar kepada saya.

## Langkah 4 — Pembersihan harian (sekali sahaja)

1. Supabase → **Database → Extensions** → hidupkan **pg_cron** dan **pg_net**.
2. Buka fail `database/manual/cron-purge-trash.sql` dalam Notepad → gantikan `<PROJECT_REF>` dan `<CRON_SECRET>` (sama dengan GitHub Secret).
3. Supabase → **SQL Editor** → tampal → **Run**.

## Langkah 5 — Semak semuanya berfungsi

Supabase → **Table Editor**: anda patut nampak jadual `profiles`, `albums`, `media`, `share_links`, `backup_history`.
Supabase → **Edge Functions**: anda patut nampak 9 fungsi (upload-init, upload-multipart, upload-complete, media-url, media-delete, share-create, share-public, export-data, purge-trash).

## Langkah 6 — Web App & APK

- Web: tab **Actions → Deploy Web App → Run workflow** (sekali selepas isi Variables). Alamat: **https://mohdrozaimi205.github.io/CloudPix/**
- APK: tab **Actions → Build Android APK → Run workflow**. Fail APK keluar di tab **Releases**.
- Repo → Settings → Pages → Source mesti **GitHub Actions**.
