# ARCHITECTURE: Aplikasi "Cocok" (versi Cloudflare)

Dokumen teknis untuk pembuat aplikasi. Pasangan dari `PRD.md` (apa yang dibuat) dan `DESIGN.md` (bagaimana tampilannya).

**Versi 2:** backend dipindah dari Supabase ke **Cloudflare** (Workers + D1 + R2).

**Prinsip:**
1. Biaya serendah mungkin (target Rp0 di paket gratis).
2. Sederhana dirawat.
3. Data kecocokan adalah aset utama, tidak terkunci pada satu teknologi.
4. **Pencarian dilakukan di HP**, sehingga cepat, bisa offline, dan hemat kuota server.

---

## 1. Gambaran besar

```
┌─────────────────────────┐   HTTPS    ┌──────────────────────────────┐
│    Aplikasi Android     │◄──────────►│     Cloudflare Worker (API)  │
│  Kotlin + Compose       │   (JSON)   │  TypeScript + Hono           │
│                         │            │  - login & peran             │
│  Room (SQLite)          │            │  - validasi & aturan bisnis  │
│  = salinan data lengkap │            │  - sinkronisasi              │
│  Pencarian dilakukan    │            └──────┬──────────────┬────────┘
│  di sini                │                   │              │
└─────────────────────────┘                   ▼              ▼
                                       ┌────────────┐  ┌────────────┐
                                       │ D1 (SQLite)│  │ R2 (foto)  │
                                       │ data pusat │  │            │
                                       └────────────┘  └────────────┘
                                              │ (tahap lanjut)
                                              ▼
                                  ┌────────────────────────────┐
                                  │ API pencarian web + AI     │
                                  │ (kunci disimpan di Worker) │
                                  └────────────────────────────┘
```

**Ide utama:** seluruh data kecocokan berupa teks dan ukurannya kecil (ratusan sampai puluhan ribu baris). Karena itu aplikasi mengunduh **salinan lengkap** ke HP, lalu mencari dan membandingkan langsung di HP. Server hanya dipakai untuk:

- menyimpan data pusat,
- sinkronisasi antar-HP,
- login dan pengaturan hak akses,
- foto,
- pencarian internet (tahap lanjut).

Hasilnya: pencarian instan dan tetap jalan tanpa internet, serta jumlah permintaan ke server sangat sedikit sehingga jauh dari batas paket gratis.

---

## 2. Tech stack

| Lapisan | Pilihan | Alasan |
|---|---|---|
| Bahasa aplikasi | Kotlin | Bahasa resmi Android |
| UI | Jetpack Compose + Material 3 | Standar Google, mudah dibuat teks dan tombol besar |
| Arsitektur aplikasi | MVVM + Repository | Tampilan, logika, dan data terpisah |
| Dependency injection | Hilt | Standar, mudah diuji |
| Database lokal | Room (SQLite) + FTS | Pencarian offline cepat |
| Jaringan | Ktor Client atau Retrofit + kotlinx.serialization | Memanggil API Worker |
| Sinkronisasi | WorkManager | Jalan di latar belakang saat ada sinyal |
| Simpan token | Android Keystore (EncryptedSharedPreferences / DataStore + Tink) | Token login aman |
| Foto | CameraX + Coil | Ambil foto dan tampilkan dengan cache |
| API | **Cloudflare Workers** (TypeScript) + framework **Hono** | Ringan, gratis, mudah diuji |
| Validasi input | Zod | Semua masukan diperiksa |
| Database pusat | **Cloudflare D1** (SQLite) | Gratis, 5 GB dalam paket gratis |
| Penyimpanan foto | **Cloudflare R2** | Gratis 10 GB, tanpa biaya trafik keluar |
| Build dan deploy | Android Studio, GitHub Actions, Wrangler | Gratis |
| Pengujian API | Vitest + `@cloudflare/vitest-pool-workers` | Menjalankan Worker dan D1 versi lokal |
| Pengujian aplikasi | JUnit, Turbine, Compose UI test | Standar Android |

Catatan: aplikasi dapat diganti ke React Native + Expo tanpa mengubah backend maupun skema data.

---

## 3. Struktur repositori

```
cocok/
 ├─ android/                     # aplikasi Android
 │   └─ app/src/main/java/.../
 │       ├─ ui/                  # layar Compose (cari, hasil, detail, bandingkan, tambah)
 │       │   └─ theme/           # warna, huruf, ukuran (sesuai DESIGN.md)
 │       ├─ domain/              # model + aturan bisnis (tanpa Android)
 │       │   ├─ model/           # Vehicle, Part, FitmentGroup, Fitment, Status
 │       │   └─ usecase/         # SearchParts, ComparePart, ConfirmFitment, ...
 │       ├─ data/
 │       │   ├─ local/           # Room: entity, DAO, FTS
 │       │   ├─ remote/          # klien API (Ktor/Retrofit)
 │       │   ├─ repository/      # gabungan lokal + remote
 │       │   └─ sync/            # WorkManager
 │       └─ di/                  # modul Hilt
 ├─ api/                         # Cloudflare Worker
 │   ├─ src/
 │   │   ├─ index.ts             # titik masuk, rute Hono
 │   │   ├─ routes/              # auth, sync, parts, vehicles, fitments, photos, research
 │   │   ├─ middleware/          # autentikasi, peran, pembatas laju
 │   │   ├─ services/            # aturan bisnis (status, bandingkan)
 │   │   └─ db/                  # kueri D1 (prepared statement)
 │   ├─ migrations/              # berkas SQL berurutan untuk D1
 │   ├─ test/
 │   └─ wrangler.toml            # konfigurasi (binding D1 dan R2)
 └─ docs/                        # PRD.md, DESIGN.md, ARCHITECTURE.md
```

Aturan: **`domain/` tidak bergantung pada Android atau jaringan**, supaya logika pencarian dan perbandingan mudah diuji.

---

## 4. Model data

### 4.1 Gagasan inti

Barang yang bentuk dan ukurannya identik dimasukkan ke satu **kelompok barang yang sama** (`fitment_groups`). Kelompok itu dihubungkan ke mobil, tahun, dan varian yang memakainya (`fitments`). Semua pertanyaan (cocok untuk mobil apa, apa penggantinya, sama atau beda) dijawab dari struktur ini.

```
 vehicles ──┐
            ├── fitments ──► fitment_groups ◄── group_parts ──► parts
 (mobil)    │   (cocok,        (barang yang          (nomor part,
            │    tahun,         sama secara fisik)     merek, foto)
            │    status)
```

### 4.2 Skema D1 (SQLite)

Berkas `api/migrations/0001_init.sql`:

```sql
-- Catatan SQLite/D1:
--  * ID berupa TEXT (UUID/ULID dibuat oleh Worker)
--  * enum  -> TEXT + CHECK
--  * JSON  -> TEXT + json_valid()
--  * waktu -> TEXT ISO 8601 UTC, selalu diisi oleh Worker (updated_at wajib diisi tiap tulis)
--  * penghapusan memakai deleted_at (soft delete) agar bisa disinkronkan ke HP

CREATE TABLE users (
  id            TEXT PRIMARY KEY,
  name          TEXT NOT NULL,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'kasir' CHECK (role IN ('pemilik','kasir')),
  active        INTEGER NOT NULL DEFAULT 1,
  created_at    TEXT NOT NULL
);

CREATE TABLE sessions (
  id           TEXT PRIMARY KEY,
  user_id      TEXT NOT NULL REFERENCES users(id),
  refresh_hash TEXT NOT NULL,        -- hanya hash yang disimpan
  device       TEXT,
  expires_at   TEXT NOT NULL,
  revoked      INTEGER NOT NULL DEFAULT 0,
  created_at   TEXT NOT NULL
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

CREATE TABLE vehicles (
  id         TEXT PRIMARY KEY,
  brand      TEXT NOT NULL,                   -- Toyota
  model      TEXT NOT NULL,                   -- Hilux
  generation TEXT,                            -- Reborn / Vigo
  year_from  INTEGER,
  year_to    INTEGER,
  facelift   INTEGER NOT NULL DEFAULT 0,
  attributes TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(attributes)), -- kabin, mesin, 2WD/4WD, transmisi
  verified   INTEGER NOT NULL DEFAULT 0,      -- model baru mulai 0
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE TABLE part_categories (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,                   -- Lampu stop, Kaca depan, Kampas rem
  position   TEXT,                            -- kiri/kanan/depan/belakang
  updated_at TEXT NOT NULL,
  deleted_at TEXT
);

CREATE TABLE parts (
  id          TEXT PRIMARY KEY,
  category_id TEXT REFERENCES part_categories(id),
  name        TEXT NOT NULL,
  brand       TEXT,
  part_number TEXT,
  is_original INTEGER NOT NULL DEFAULT 0,     -- asli (OEM) atau merek lain
  photo_key   TEXT,                           -- kunci objek di R2
  notes       TEXT,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  deleted_at  TEXT
);
CREATE INDEX parts_number_idx ON parts (part_number);

CREATE TABLE fitment_groups (
  id          TEXT PRIMARY KEY,
  category_id TEXT NOT NULL REFERENCES part_categories(id),
  label       TEXT,                           -- "Stop lamp kiri Hilux Reborn"
  notes       TEXT,
  updated_at  TEXT NOT NULL,
  deleted_at  TEXT
);

CREATE TABLE group_parts (
  group_id   TEXT NOT NULL REFERENCES fitment_groups(id),
  part_id    TEXT NOT NULL REFERENCES parts(id),
  status     TEXT NOT NULL DEFAULT 'mungkin' CHECK (status IN ('pasti','mungkin','tidak_cocok')),
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  PRIMARY KEY (group_id, part_id)
);

CREATE TABLE sources (
  id         TEXT PRIMARY KEY,
  kind       TEXT NOT NULL CHECK (kind IN ('internet','katalog','toko')),
  url        TEXT,
  note       TEXT,
  fetched_at TEXT NOT NULL
);

CREATE TABLE fitments (
  id           TEXT PRIMARY KEY,
  group_id     TEXT NOT NULL REFERENCES fitment_groups(id),
  vehicle_id   TEXT NOT NULL REFERENCES vehicles(id),
  year_from    INTEGER,
  year_to      INTEGER,
  constraints  TEXT NOT NULL DEFAULT '{}' CHECK (json_valid(constraints)),  -- varian yang berpengaruh
  status       TEXT NOT NULL DEFAULT 'mungkin' CHECK (status IN ('pasti','mungkin','tidak_cocok')),
  source_id    TEXT REFERENCES sources(id),
  confirmed_by TEXT REFERENCES users(id),
  confirmed_at TEXT,
  created_at   TEXT NOT NULL,
  updated_at   TEXT NOT NULL,
  deleted_at   TEXT
);
CREATE INDEX fitments_vehicle_idx ON fitments (vehicle_id, year_from, year_to);
CREATE INDEX fitments_group_idx   ON fitments (group_id);
CREATE INDEX fitments_updated_idx ON fitments (updated_at);

CREATE TABLE confirmations (
  id         TEXT PRIMARY KEY,
  fitment_id TEXT NOT NULL REFERENCES fitments(id),
  user_id    TEXT NOT NULL REFERENCES users(id),
  result     TEXT NOT NULL CHECK (result IN ('cocok','tidak_cocok')),
  note       TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE search_synonyms (
  term       TEXT PRIMARY KEY,                -- 'rebon'
  canonical  TEXT NOT NULL,                   -- 'reborn'
  updated_at TEXT NOT NULL
);

CREATE TABLE change_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name TEXT NOT NULL,
  record_id  TEXT NOT NULL,
  action     TEXT NOT NULL,
  old_value  TEXT,
  new_value  TEXT,
  user_id    TEXT REFERENCES users(id),
  created_at TEXT NOT NULL
);

-- Pembatas laju (login dan pencarian internet)
CREATE TABLE usage_counters (
  key    TEXT NOT NULL,                       -- 'login:ip' / 'research:2026-11-17'
  window TEXT NOT NULL,
  count  INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (key, window)
);

-- Indeks untuk sinkronisasi (tarik perubahan sejak waktu tertentu)
CREATE INDEX vehicles_updated_idx   ON vehicles (updated_at);
CREATE INDEX parts_updated_idx      ON parts (updated_at);
CREATE INDEX groups_updated_idx     ON fitment_groups (updated_at);
CREATE INDEX group_parts_updated_idx ON group_parts (updated_at);
CREATE INDEX categories_updated_idx ON part_categories (updated_at);
CREATE INDEX synonyms_updated_idx   ON search_synonyms (updated_at);
```

Semua kueri memakai **prepared statement** dengan parameter terikat (tidak ada penggabungan string) untuk mencegah SQL injection.

---

## 5. API (Cloudflare Worker)

Semua rute di bawah `/v1`, format JSON, dan (kecuali login) wajib memakai token.

| Metode | Rute | Peran | Fungsi |
|---|---|---|---|
| POST | `/auth/login` | publik, dibatasi laju | Login dengan username + kata sandi, mengembalikan token |
| POST | `/auth/refresh` | login | Perpanjang sesi |
| POST | `/auth/logout` | login | Cabut sesi |
| GET | `/sync?since=...` | semua | Perubahan data sejak waktu tertentu |
| GET | `/search?q=...` | semua | Pencarian di server (cadangan dan untuk tahap web) |
| GET | `/compare` | semua | Bandingkan (cadangan; utama dihitung di HP) |
| POST | `/parts`, `/vehicles`, `/groups` | semua | Tambah data. Kasir: status dipaksa `mungkin` |
| PATCH | `/parts/:id`, `/vehicles/:id` | pemilik | Ubah data |
| POST | `/fitments` | semua | Tambah kecocokan. Kasir: status dipaksa `mungkin` |
| POST | `/fitments/:id/confirmations` | semua | Konfirmasi "cocok / tidak cocok" |
| PATCH | `/fitments/:id/status` | **pemilik** | Tetapkan `pasti` atau `tidak_cocok` |
| PUT | `/photos` | semua | Unggah foto (sudah dikompres) ke R2 |
| GET | `/photos/:key` | semua | Ambil foto (dengan cache) |
| POST | `/users` | **pemilik** | Buat atau nonaktifkan akun kasir |
| GET | `/export` | **pemilik** | Ekspor data (CSV/JSON) untuk cadangan |
| POST | `/research` | pemilik (tahap lanjut) | Pencarian internet + AI |

Aturan di setiap rute: validasi masukan (Zod), periksa peran, catat ke `change_log` untuk perubahan penting.

---

## 6. Login dan hak akses

Karena Cloudflare tidak punya login pengguna bawaan seperti Supabase Auth, login dibuat sendiri dan **sengaja dibuat sederhana**.

**Alur:**
1. Pemilik membuat akun kasir (username + kata sandi awal).
2. Pengguna login → Worker memeriksa kata sandi → mengirim **access token** (JWT, berlaku singkat) dan **refresh token** (berlaku lama, mis. 30 hari).
3. Hanya **hash** refresh token yang disimpan di tabel `sessions`, sehingga bisa dicabut kapan saja (HP hilang, kasir keluar).
4. Aplikasi menyimpan token di Android Keystore.
5. Setiap permintaan melewati middleware: token valid → ambil peran → izinkan atau tolak.

**Hak akses (ditegakkan di Worker):**

| Tindakan | Kasir | Pemilik |
|---|---|---|
| Membaca semua data | Ya | Ya |
| Menambah mobil, barang, kecocokan | Ya, status dipaksa `mungkin` | Ya |
| Konfirmasi "sudah dipakai, cocok" | Ya | Ya |
| Menetapkan `pasti` atau `tidak_cocok` | **Tidak** | Ya |
| Mengubah atau menghapus data | Tidak | Ya |
| Membuat akun, ekspor, riwayat | Tidak | Ya |

**Keamanan:**
- Kata sandi disimpan sebagai hash (PBKDF2 lewat WebCrypto, dengan *salt* unik). Jangan pernah menyimpan kata sandi asli.
- Login dibatasi laju (mis. 5 percobaan per 10 menit per IP dan username) lewat `usage_counters`.
- Kunci rahasia (penandatangan JWT, kunci API pencarian dan AI) disimpan sebagai **secret Wrangler**, bukan di kode atau aplikasi.
- Pendaftaran terbuka tidak ada. Akun hanya dibuat pemilik.
- Semua koneksi HTTPS. Aplikasi menolak koneksi tidak aman.
- Karena aturan akses ada di kode (bukan di database), **setiap rute wajib punya uji otomatis** yang membuktikan kasir ditolak untuk tindakan pemilik.

> **Perlu diuji lebih awal:** hashing kata sandi memakai waktu CPU, dan paket gratis Workers punya batas waktu CPU per permintaan (sumber berbeda menyebut angka berbeda, jadi cek dokumen resmi). Jika login gagal karena batas ini, pilihannya: kurangi iterasi dalam batas aman, pakai Cloudflare Access (cek ketersediaan paket gratis dan syaratnya), atau naik ke paket Workers berbayar yang kecil. Karena login jarang dilakukan (token berlaku lama), dampaknya kecil.

---

## 7. Pencarian dan logika inti

### 7.1 Pencarian (dilakukan di HP)

Contoh masukan: `stop hilux rebon 2018`

1. **Bersihkan:** huruf kecil, buang tanda baca.
2. **Pisahkan** menjadi kata. Angka 4 digit dianggap tahun.
3. **Ganti sinonim** dari `search_synonyms` (rebon → reborn, cakrem → cakram).
4. **Kenali:** kata yang cocok dengan mobil → mobil, yang cocok dengan jenis barang → kategori, angka → tahun.
5. **Cocokkan** dengan FTS Room untuk kandidat, lalu urutkan memakai kemiripan teks (mis. jarak Levenshtein/trigram) di Kotlin.
6. **Jika ada yang kurang** (mobil tidak jelas, tahun kosong, jenis barang tidak disebut), kembalikan **pilihan yang bisa diketuk**, bukan galat.
7. **Susun hasil:** pasti di atas, lalu mungkin; tampilkan label dan sumber.

Karena datanya kecil, pencocokan di HP cepat dan tidak membutuhkan fitur kemiripan teks di database server. Endpoint `/search` di server hanya cadangan (misalnya untuk versi web di masa depan, memakai FTS5 D1).

### 7.2 Mencari barang untuk mobil dan tahun

```
vehicle + tahun + kategori
  → fitments (vehicle_id, year_from <= tahun <= year_to, kategori)
  → group
  → parts di group
  → urutkan: status pasti dulu
```

### 7.3 Mode Bandingkan

Masukan: kategori, mobil A + tahun, mobil B + tahun.

```
grupA = group yang memuat (A, tahunA, kategori)
grupB = group yang memuat (B, tahunB, kategori)

jika grupA dan grupB punya irisan  → SAMA  (status = terendah dari kedua fitment)
jika keduanya punya data PASTI tapi tidak beririsan → BEDA
selain itu                          → BELUM TAHU
```

**Penting:** "tidak ada data" tidak boleh menghasilkan "BEDA". Beda hanya jika kedua sisi sudah punya data yang pasti.

### 7.4 Cari pengganti

Dari satu `part`, ambil semua `group_parts` di kelompok yang sama, lalu urutkan menurut status dan ketersediaan (setelah fitur stok ada).

### 7.5 Siklus status

```
 (sumber internet / kasir) ──► MUNGKIN ──(kasir: cocok)──► MUNGKIN + konfirmasi
                                  │                               │
                                  │                  (pemilik menyetujui)
                                  ▼                               ▼
                            TIDAK COCOK ◄──(pemilik)─────────── PASTI
```

- Hanya **pemilik** yang dapat mengubah ke `pasti` atau `tidak_cocok` (ditegakkan di Worker).
- Kasir hanya menambah `confirmations`.
- Jika data internet bertentangan dengan data `pasti`, data `pasti` tetap berlaku.

---

## 8. Sinkronisasi dan offline

**Pendekatan:** aplikasi selalu membaca dari Room. Sinkronisasi memperbarui Room dari server.

**Menarik data:** `GET /sync?since=<waktu>` mengembalikan baris yang `updated_at`-nya lebih baru, termasuk yang punya `deleted_at` (supaya HP ikut menghapus). Pertama kali (tanpa `since`) mengembalikan seluruh data.

- Server memakai `>=` pada `since`, dan HP menghapus duplikat berdasarkan `id`, agar tidak ada perubahan yang terlewat saat dua perubahan punya waktu yang sama.
- Hasil dipecah per halaman (mis. 500 baris) untuk data besar.
- Indeks `updated_at` di setiap tabel memastikan sinkronisasi hanya membaca baris yang berubah (penting untuk batas pembacaan baris harian D1).

**Menulis:**
- Saat online: langsung ke API.
- Saat offline: masuk antrean lokal, dikirim WorkManager saat ada sinyal.
- **Konflik:** perubahan terbaru menang, kecuali status yang ditetapkan pemilik, yang tidak bisa ditimpa kasir (server menolak).

**Foto:** diunduh sesuai kebutuhan dan disimpan Coil. Ukuran dikompres sebelum diunggah (target 100-200 KB), diunggah lewat Worker ke R2. Foto dilayani dengan header cache panjang agar permintaan berulang tidak membebani kuota.

---

## 9. Pencarian internet

### Tahap P1: semi-manual (biaya Rp0)

1. Pengguna menekan **Cari di internet** untuk barang dan mobil tertentu.
2. Aplikasi menyiapkan teks pertanyaan siap salin (barang, mobil, tahun, minta nomor part dan padanan).
3. Pengguna menyalinnya ke chatbot, lalu menempel jawaban ke **formulir terstruktur** (nomor part, mobil, tahun, sumber).
4. Disimpan sebagai `mungkin` dengan `sources.kind = 'internet'`.

### Tahap P2: otomatis

```
Aplikasi ──► Worker /research ──► API pencarian web ──► AI merangkum (JSON)
                                                              │
        D1 ◄── simpan sebagai 'mungkin' ◄── validasi (Zod) ◄──┘
```

- Hanya dipanggil jika data tidak ada di database (hasil disimpan sehingga tidak dicari dua kali).
- Batas pemakaian per hari (`usage_counters`) untuk mengendalikan biaya.
- Keluaran AI diminta dalam format JSON tetap, lalu **divalidasi** sebelum disimpan.
- Dua sumber atau lebih yang menyebut nomor part sama menaikkan keyakinan, tetapi **tetap berlabel mungkin** sampai pemilik menyetujui.
- Kunci API hanya ada di secret Worker.

---

## 10. Batas paket gratis dan penanganannya

Angka berikut berasal dari ringkasan pihak ketiga per akhir 2026. **Periksa halaman harga resmi Cloudflare sebelum memulai dan secara berkala**, karena bisa berubah.

| Layanan | Batas gratis (perkiraan) | Penanganan |
|---|---|---|
| D1 | 5 GB data; 5 juta baris dibaca/hari; 100 ribu baris ditulis/hari. Per 1 September 2026, melewati batas harian membuat kueri gagal | Sinkronisasi hanya membaca baris yang berubah dan memakai indeks. Pantau pemakaian di dasbor. |
| R2 | 10 GB; tanpa biaya trafik keluar | Kompres foto, hapus foto tak terpakai. |
| Workers | 100 ribu permintaan/hari | Pencarian di HP + cache foto membuat permintaan sangat sedikit. |

**Perkiraan pemakaian:** 5 pengguna × sekitar 200 permintaan/hari = sekitar 1.000 permintaan/hari, jauh di bawah batas.

**Cadangan:**
- Endpoint `/export` (pemilik) menghasilkan CSV/JSON yang dibagikan ke Google Drive lewat menu bagikan HP.
- Pengembang dapat menjalankan `wrangler d1 export` secara berkala.
- Cek apakah fitur pemulihan ke waktu tertentu (Time Travel) D1 tersedia dan berapa lama riwayatnya di paket yang dipakai.
- Jadwal otomatis (Cron Triggers) bisa dipertimbangkan, cek dulu ketersediaan di paket gratis.

---

## 11. Pengujian

| Jenis | Cakupan |
|---|---|
| Unit (HP) | Normalisasi pencarian, aturan Bandingkan, siklus status |
| Data | Skenario kecocokan (Hilux Vigo vs Reborn, bensin vs diesel, facelift) |
| API | Setiap rute: berhasil, masukan salah, tanpa token |
| **Keamanan (wajib)** | **Kasir ditolak** untuk menetapkan "pasti", mengubah data, membuat akun, dan ekspor |
| Sinkronisasi | Perubahan dan penghapusan ikut sampai ke HP lain; waktu `updated_at` sama tidak menghilangkan data |
| UI | Alur cari → hasil → detail → konfirmasi |
| Pengguna | Uji dengan mekanik dan pemilik (lihat DESIGN.md bagian 13) |

---

## 12. Build dan rilis

1. Kode di GitHub (repositori privat), satu repositori untuk `android/` dan `api/`.
2. **API:** GitHub Actions menjalankan uji, lalu `wrangler deploy`. Token Cloudflare disimpan sebagai secret GitHub.
3. **Database:** perubahan skema lewat berkas di `api/migrations/` dan `wrangler d1 migrations apply`. Selalu terapkan ke lingkungan uji dulu.
4. **Aplikasi:** GitHub Actions membuat APK setiap ada versi baru; dibagikan lewat tautan atau WhatsApp (instal manual), atau Google Play bila diinginkan.
5. Dua lingkungan: **uji** (data contoh) dan **produksi** (data toko).

---

## 13. Keputusan teknis

| Keputusan | Alasan | Alternatif |
|---|---|---|
| Cloudflare (Workers + D1 + R2) | Kuota gratis besar, satu penyedia, tidak ada jeda proyek | Supabase (lebih cepat jadi, aturan akses di database) |
| Pencarian di HP | Data kecil, cepat, offline, hemat kuota | Pencarian di server (D1 FTS5) |
| Login dibuat sendiri | Cloudflare tidak punya login pengguna bawaan | Cloudflare Access, penyedia login pihak ketiga |
| Aturan akses di kode Worker | Cara yang tersedia di Cloudflare | RLS (hanya ada di PostgreSQL/Supabase) |
| Status per hubungan kecocokan | Data internet dan data toko beda tingkat keyakinan | Satu status per barang (terlalu kasar) |
| Konfirmasi oleh pemilik | Mencegah data salah menjadi "pasti" | Otomatis setelah N kasir setuju |
| "Belum tahu" ≠ "Beda" | Menghindari jawaban salah akibat data kosong | |
| Soft delete (`deleted_at`) | HP perlu tahu data yang dihapus | Hapus permanen (HP tidak ikut menghapus) |
| Pencarian internet lewat Worker | Kunci rahasia aman, biaya dapat dibatasi | Panggil dari aplikasi (tidak aman) |

**Perbedaan utama dari versi Supabase:** tidak ada RLS dan tidak ada `pg_trgm` (diganti aturan akses di Worker dan pencarian di HP), dan ada API serta login yang harus dibuat sendiri. Sebagai gantinya, kuota gratis lebih besar dan tidak ada jeda proyek.

---

## 14. Rencana awal pengerjaan

1. Siapkan akun Cloudflare, proyek Worker, database D1, dan bucket R2. Terapkan `0001_init.sql`.
2. Bangun `auth` (login, refresh, peran) beserta uji keamanannya.
3. Isi data contoh (Hilux Vigo/Reborn/2026, Avanza) dan 30-50 barang paling sering dicari.
4. Bangun rute `sync`, `parts`, `fitments`, `confirmations`, `photos`.
5. Bangun layar Cari, Hasil, Detail dengan Room dan sinkronisasi.
6. Tambah Bandingkan dan konfirmasi.
7. Uji di toko, perbaiki.
8. Lanjut ke P1 (cari internet semi-manual, impor/ekspor, riwayat), lalu P2.
