# Cocok — Cek Kecocokan & Pencocokan Suku Cadang

Aplikasi Android untuk toko suku cadang: satu barang dimasukkan lewat **foto atau kata kunci**, aplikasi menampilkan **kandidat barang yang sama atau penggantinya**, lalu membandingkan bukti sebelum ada yang dijual.

Pencarian berjalan **di HP**. Data kecocokan disimpan lokal di Room, sehingga tetap terbuka tanpa internet dan tidak memakan kuota server. Cloudflare Worker + D1 disiapkan sebagai data pusat untuk sinkronisasi antar-HP (belum dihubungkan).

| | |
|---|---|
| Nama kerja | Cocok |
| Application ID | `tech.cocok.app` |
| Versi | `0.1.0` (debug) |
| Android | minSdk 26, target/compile 35 |
| Backend | Cloudflare Workers + D1 (+ R2 belum aktif) |
| Repositori | `github.com/baniputrabangsawan/spartan` |

---

## 1. Latar belakang

Di toko, kasir dan pemilik sering harus memastikan apakah sebuah barang cocok untuk mobil tertentu, atau bisa diganti barang lain saat stok habis:

- Lampu stop Hilux Reborn sama atau tidak dengan Hilux yang baru?
- Kampas rem depan Avanza bisa dipakai di mobil lain?
- Kaca Hilux 2005–2015 sama atau tidak dengan tahun 2020?

Jawabannya sekarang dicari manual di Google atau chatbot: lambat, tanpa nomor part, tanpa tingkat keyakinan, dan tidak tersimpan.

**Pengguna:** pemilik (memastikan data benar, menetapkan status akhir), kasir (pemakai paling sering), mekanik (butuh tampilan paling sederhana). Target: orang yang baru melihat aplikasi bisa mencari barang dalam 1 menit.

**Tujuan:** jawaban < 10 detik, keyakinan yang jelas (pasti/mungkin), database milik toko yang makin lengkap, retur karena salah kecocokan berkurang.

**Bukan tujuan (versi ini):** sistem kasir/pembukuan, stok & harga, penjualan online, iPhone.

---

## 2. Status implementasi

Jujur membedakan yang sudah jalan dari yang masih rencana.

**Sudah berjalan**

- Pencarian di HP: toleran salah ketik (`rebon` → Reborn, `avansa` → Avanza), deteksi mobil/kategori/tahun, sinonim.
- Label hasil `PASTI / MUNGKIN / TIDAK COCOK / BELUM ADA DATA` dengan warna + ikon + kata.
- Layar Cari, Hasil, Detail, Bandingkan, Tambah, Pengaturan.
- Mode Bandingkan dua mobil (SAMA / BEDA / BELUM TAHU) dengan aturan "belum ada data ≠ beda".
- Konfirmasi kasir dan penetapan status final khusus pemilik.
- Penyimpanan lokal Room v2 dengan migrasi 1→2, transaksi, dan rollback aman.
- Pencocokan barang: sumber (foto/kata kunci/data toko) → kandidat → perbandingan spesifikasi + bukti pemilik.
- Kemiripan bentuk foto di HP (MediaPipe ImageEmbedder + MobileNet-v3-small).
- Pencarian gambar internet (Google Images) → kartu native; unggah foto untuk pencarian kemiripan setelah persetujuan per foto.
- Worker `cocok-api`: `GET /v1/health` (cek skema D1 + akses R2), rute lain `404`.

**Belum tersedia**

- Login pengguna sungguhan, JWT, rate limit, dan penegakan peran di server. Pemilihan peran di aplikasi masih **kontrol demo**, bukan keamanan.
- Sinkronisasi antar-HP, outbox tulis offline, ekspor/impor CSV.
- Foto barang tersimpan permanen (R2 belum aktif; foto masih di cache privat aplikasi).
- Rute data/foto di Worker; Android belum memanggil Worker sama sekali.
- Riwayat "Terakhir dicari" dan sesi demo hanya di memori.

---

## 3. Alur utama: pencocokan barang

```
sumber (foto / kata kunci / barang di data toko)
   ↓
kandidat: barang di grup yang sama, hasil pencarian katalog, hasil gambar internet
   ↓
perbandingan: dua identitas berdampingan + baris spesifikasi (sama / beda / belum ada)
   ↓
daftar "yang masih perlu diperiksa" untuk data kosong
   ↓
catatan pemilik (mobil + tahun + varian) → label terverifikasi
```

Aturan yang tidak boleh dilanggar:

- **Penemuan bukan verifikasi.** Kemiripan bentuk, nomor part yang sama di internet, atau satu grup barang hanya menghasilkan **kandidat**.
- Spesifikasi yang sama **tidak** menaikkan hasil menjadi "pengganti". Nilainya tetap `Kandidat — belum terverifikasi`.
- Label `Barang sama` / `Pengganti terverifikasi` **hanya** berasal dari `MatchEvidence` pemilik yang terikat ke mobil + tahun + varian. Bukti tidak bocor ke mobil lain.
- Data yang kosong menjadi **pemeriksaan fisik** ("ukur jarak baut", "cocokkan soket"), bukan dianggap lolos.
- Item hasil edit atau dari internet tidak mewarisi identitas katalog, sehingga tidak bisa "membawa" verifikasi lama.

---

## 4. Arsitektur

```
┌─────────────────────────┐   HTTPS    ┌──────────────────────────────┐
│    Aplikasi Android     │◄──────────►│     Cloudflare Worker (API)  │
│  Kotlin + Compose       │   (JSON)   │  TypeScript + Hono           │
│                         │            │  - login & peran (rencana)   │
│  Room (SQLite)          │            │  - validasi & aturan bisnis  │
│  = salinan data lengkap │            │  - sinkronisasi (rencana)    │
│  Pencarian di sini      │            └──────┬──────────────┬────────┘
└─────────────────────────┘                   ▼              ▼
                                       ┌────────────┐  ┌────────────┐
                                       │ D1 (SQLite)│  │ R2 (foto)  │
                                       │ data pusat │  │ belum aktif│
                                       └────────────┘  └────────────┘
```

Prinsip: data kecocokan hanya teks dan kecil (puluhan ribu baris), jadi seluruh salinan diunduh ke HP dan pencarian dilakukan di sana. Server hanya untuk data pusat, sinkronisasi, login, dan foto.

### Alur data di aplikasi

```
Compose screens → CocokViewModel / MatchViewModel
                → use case domain murni (SearchParts, CompareParts, MatchParts)
                → CatalogRepository → Room (satu transaksi per perubahan)
```

`domain/` tidak boleh bergantung pada Android atau jaringan. `CocokApplication` menyediakan repository (DI manual, tanpa Hilt).

### Aturan penulisan data

- Perubahan katalog baru diumumkan ke UI **setelah** transaksi Room commit.
- Sebelum commit, `PRAGMA foreign_key_check` dijalankan agar penolakan foreign key tidak meninggalkan koneksi SQLite dalam transaksi gagal.
- Data contoh hanya dimasukkan sekali, memakai flag inisialisasi yang persisten; katalog yang dikosongkan tetap kosong.
- Item baru dari hasil pencarian selalu membuat grup `MUNGKIN` sendiri.

---

## 5. Model data

Gagasan inti: barang yang bentuk dan ukurannya identik masuk ke satu **kelompok barang yang sama** (`fitment_groups`). Kelompok itu dihubungkan ke mobil, tahun, dan varian (`fitments`). Semua pertanyaan dijawab dari struktur ini.

```
 vehicles ──┐
            ├── fitments ──► fitment_groups ◄── group_parts ──► parts
 (mobil)    │   (cocok,        (barang yang          (nomor part,
            │    tahun,         sama secara fisik)     merek, foto)
            │    status, varian)
```

Entitas domain: `Vehicle`, `PartCategory`, `Part`, `FitmentGroup`, `GroupPart`, `Fitment`, `Status`, `Confirmation`, `MatchItem`, `MatchContext`, `MatchEvidence`.

**Room v2** (`android/app/schemas/`):

| Tabel | Isi |
|---|---|
| `vehicles`, `part_categories` | mobil dan jenis barang |
| `parts` | barang + `partNumber`, `photoKey`, **`specifications`** (v2) |
| `fitment_groups`, `group_parts` | kelompok barang sama dan isinya (status per barang) |
| `fitments` | kecocokan per mobil + rentang tahun + `constraints` (varian) + status |
| `synonyms` | padanan kata sehari-hari |
| `confirmations` | konfirmasi kasir/pemilik |
| `match_evidence` | (v2) bukti pemilik: dua barang, mobil, tahun, varian, jenis, catatan |
| `catalog_metadata` | flag inisialisasi seed |

**D1** (`api/migrations/0001_init.sql`): cermin tabel di atas untuk pemakaian pusat, ditambah `users`, `sessions`, `sources`, `change_log`, `sync_log` + trigger. ID `TEXT` (dibuat klien), enum `TEXT + CHECK`, JSON `TEXT + json_valid()`, waktu ISO 8601 UTC, hapus = soft delete (`deleted_at`).

---

## 6. Aturan inti

### 6.1 Pencarian

Contoh masukan: `stop hilux rebon 2018` → huruf kecil, buang tanda baca → pisah kata, angka 4 digit = tahun → ganti sinonim → kenali mobil/kategori → cocokkan teks (Levenshtein + awalan kata) → label.

- **Kata mengandung angka dicocokkan persis.** Satu digit berbeda pada nomor part (`81560` vs `81150`) adalah barang berbeda.
- Nomor part utuh diperiksa lebih dulu, sebagai satu kesatuan (bukan potongan kata).
- Koreksi OCR (`O`→`0`, `I/L`→`1`, `S`→`5`, `B`→`8`) hanya dipakai di jalur foto dan **hanya bila hasilnya tunggal**; ambigu berarti "tidak tahu".
- Bila mobil disebut, hanya kelompok yang punya kecocokan untuk mobil itu yang boleh muncul.

### 6.2 Cocok untuk mobil dan tahun

```
vehicle + tahun + kategori → fitments → group → parts → urut status (pasti dulu)
```

Kecocokan bersifat kontekstual: barang + mobil + tahun + varian. Kaca depan, kaca pintu, dan lampu stop untuk mobil yang sama bisa berbeda jawabannya.

### 6.3 Bandingkan

```
grupA ∩ grupB ≠ ∅                  → SAMA
keduanya PASTI dan tidak beririsan → BEDA
selain itu                         → BELUM TAHU
```

`BELUM_ADA_DATA` adalah nilai turunan, tidak pernah disimpan, dan **tidak** diperlakukan sama dengan `TIDAK_COCOK`.

### 6.4 Siklus status

```
(internet / kasir) ──► MUNGKIN ──(kasir: cocok)──► MUNGKIN + konfirmasi
                          │                            │
                          ▼                            ▼
                     TIDAK COCOK ◄──(pemilik)──────  PASTI
```

- Hanya **pemilik** yang boleh menetapkan `pasti` / `tidak_cocok`.
- Kasir hanya menambah konfirmasi.
- Data pemilik menang atas data internet.
- Hasil internet selalu mulai `mungkin`, dengan peringatan "Cek barang langsung sebelum dijual".

### 6.5 Foto dan internet

- OCR berjalan lokal (ML Kit); foto tidak diunggah untuk OCR.
- Kemiripan bentuk memakai embedding lokal. Skor kosinus = kemiripan bentuk, **bukan** peluang cocok. Tanpa foto di katalog, aplikasi menyatakan belum ada indeks foto.
- Pencarian gambar internet: kata kunci dikirim ke Google Images (`q`, `udm=2`); respons diurai menjadi judul, thumbnail, URL gambar, dan URL sumber, lalu ditampilkan sebagai kartu Compose native.
- Pencarian kemiripan foto mengunggah foto **hanya setelah persetujuan per foto**, dibatasi HTTPS, JPEG tanpa metadata, dan batas ukuran. CAPTCHA/persetujuan Google tidak pernah dilewati.
- Format respons web Google bukan API resmi dan bisa berubah kapan saja.

---

## 7. Desain antarmuka

Prinsip: **seperti papan tulis di toko — besar, jelas, jawabannya langsung kelihatan.**

1. Tulisan besar (tidak ada teks < 16 sp).
2. Satu layar, satu pekerjaan.
3. Jawaban dulu, penjelasan belakangan.
4. Bahasa sehari-hari, tanpa istilah teknis.
5. Jangan mengandalkan warna saja.

### 7.1 Label hasil

| Label | Warna | Ikon | Teks pendamping |
|---|---|---|---|
| **PASTI COCOK** | hijau | centang | "Sudah dicek, aman dipasang" |
| **MUNGKIN COCOK** | kuning | tanda tanya | "Belum dicek. Lihat barangnya dulu." |
| **TIDAK COCOK** | merah | silang | "Jangan dipasang" |
| **BELUM ADA DATA** | abu-abu | minus | "Belum ada info. Bisa dicari." |

Label tidak pernah lebih kecil dari nama barang.

### 7.2 Warna

| Fungsi | Kode |
|---|---|
| Utama (tombol utama) | `#1B5E9B` |
| Latar | `#FFFFFF` |
| Teks utama | `#1A1C1E` |
| Teks pendamping | `#44474E` |
| Pasti (teks/latar) | `#1E7B34` / `#E6F4EA` |
| Mungkin (teks/latar) | `#7A4F00` / `#FFF3CD` |
| Tidak cocok (teks/latar) | `#B3261E` / `#FDE7E9` |
| Belum ada data (teks/latar) | `#5F6368` / `#EEF0F2` |

### 7.3 Tipografi

Judul layar 24 sp tebal · nama barang 22 sp tebal · label hasil 20 sp tebal kapital · teks biasa 18 sp · catatan 16 sp. Aplikasi mengikuti pengaturan ukuran huruf HP.

### 7.4 Tombol dan menu

- Tinggi tombol minimal 56 dp, lebar penuh untuk tombol utama, jarak antar tombol 12 dp.
- Selalu ada tulisan, bukan ikon saja. Maksimal satu tombol utama per layar.
- Menu bawah hanya tiga: **CARI · BANDINGKAN · TAMBAH**. Pengaturan di ikon pojok kanan atas.

### 7.5 Layar

| Layar | Isi |
|---|---|
| **Cari** | kotak cari besar, tombol "Cari padanan barang", chip kategori/mobil sering dicari, riwayat |
| **Pencocokan** | sumber (foto/galeri/kata kunci/data toko) → kandidat beralasan → perbandingan + verifikasi pemilik |
| **Hasil** | satu kartu per barang, foto kiri, label; `tidak_cocok` disembunyikan tapi bisa dibuka |
| **Detail** | foto, nomor part asli/merek lain, "Cocok untuk", catatan, tombol konfirmasi (tombol status hanya untuk pemilik) |
| **Bandingkan** | tiga kotak berurutan → satu jawaban besar SAMA/BEDA/BELUM TAHU |
| **Tambah** | tiga pilihan besar: barang, mobil, hubungkan |

### 7.6 Kondisi khusus

| Kondisi | Tampilan |
|---|---|
| Sedang mencari | lingkaran + "Sedang mencari…" |
| Tidak ada hasil | "Belum ada data." + tombol tambah sendiri / cari di internet |
| Tanpa internet | pita kuning "Tidak ada internet. Data di HP tetap bisa dipakai." |
| Berhasil simpan | pesan hijau singkat |
| Ada masalah | pesan jelas + satu tombol coba lagi |

Aksesibilitas: kontras ≥ 4.5:1, area ketuk 56 dp, bisa dipakai satu tangan, tanpa animasi mengganggu.

---

## 8. Cara menjalankan

Alat yang dipakai: **JDK 17**, Android SDK 35, Gradle wrapper di repo, Node ≥ 22.12.0 + npm untuk Worker.

### 8.1 Aplikasi Android

```bash
cd android

./gradlew :app:assembleDebug          # APK debug
./gradlew :app:testDebugUnitTest      # uji JVM (logika pencarian/perbandingan)
./gradlew :app:connectedDebugAndroidTest   # uji Room/migrasi/embedder/UI (butuh HP/emulator)
./gradlew :app:lintDebug              # lint AGP

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n tech.cocok.app/.MainActivity
```

Lokasi SDK disimpan di `android/local.properties` (tidak di-commit). Data aplikasi tetap ada saat `install -r`; gunakan `adb uninstall` hanya bila memang ingin menghapus.

Model `assets/mobilenet_v3_small.tflite` (Apache-2.0) harus tetap tidak dikompres — sudah diatur `androidResources { noCompress += "tflite" }`.

### 8.2 Worker (Cloudflare)

```bash
cd api

npm ci                # pasang dependensi sesuai package-lock.json
npm run typecheck     # tsc --noEmit
npm run build         # wrangler deploy --dry-run (bundle saja)
npm run migrate:local # terapkan migrations ke D1 lokal (env staging)
npm run dev           # wrangler dev --env staging
```

Perintah remote **mengubah infrastruktur** — jalankan hanya bila memang diminta:

```bash
npm run migrate:staging      # D1 uji
npm run deploy:staging       # Worker uji
npm run migrate:production   # D1 produksi
npm run deploy               # Worker produksi
```

Login Wrangler memakai akun Cloudflare sendiri; token OAuth MCP bukan kredensial Wrangler.

### 8.3 Resource Cloudflare

| Lingkungan | D1 | Worker |
|---|---|---|
| Produksi | `cocok-db` (`be04939d-…`) | `https://cocok-api.baniputrabangsawan11.workers.dev` |
| Uji | `cocok-db-staging` (`96782bd6-…`) | `https://cocok-api-staging.baniputrabangsawan11.workers.dev` |

Status nyata: keduanya dideploy dengan binding D1 saja. `/v1/health` menjawab `503 storage_unavailable` karena **R2 belum aktif** (aktivasi meminta metode pembayaran). Rute data/foto belum ada dan menjawab `404`, jadi katalog produksi tidak terbuka ke publik.

Saat R2 aktif: buat bucket privat `cocok-photos` dan `cocok-photos-staging`, pasang binding `PHOTOS`, lalu ulangi smoke sampai `200`.

### 8.4 Rahasia

Tidak ada kunci rahasia di repo. Rencana secret Wrangler: `JWT_SECRET` dan `PEPPER`. `api/.env*` dan `api/.dev.vars*` di-ignore.

---

## 9. Pengujian

| Jenis | Cakupan | Status |
|---|---|---|
| JVM (`:app:testDebugUnitTest`) | pencarian, sinonim, nomor part vs koreksi OCR, Bandingkan, aturan peran, aturan pencocokan | **51 lulus** |
| Instrumentasi (`:app:connectedDebugAndroidTest`) | Room buka-ulang/rollback/tulis bersamaan/seed kosong, migrasi 1→2, embedder nyata, 3 alur UI Compose | **12 lulus** |
| Worker | `typecheck` + dry-run build + migrasi lokal | lulus |
| Parser gambar | 6 uji memakai rekaman respons Google nyata | lulus |

Dijalankan pada Infinix X6885 (Android 16) dan emulator API 35.

**Belum diuji:** kamera fisik, rotasi/process-recreation, CAPTCHA Google nyata, unggah foto lewat Lens di perangkat, dan seluruh alur login/keamanan (belum ada implementasinya). Jangan menganggap jalur itu lulus.

---

## 10. Batas dan risiko

| Risiko | Penanganan |
|---|---|
| Data internet salah | selalu `mungkin`, butuh konfirmasi pemilik, peringatan cek fisik |
| Kemiripan foto disalahartikan sebagai kecocokan | skor hanya petunjuk; label butuh bukti pemilik |
| Parser Google berubah / CAPTCHA | pesan kesalahan jelas, tidak ada bypass, tidak ada data palsu |
| Peran masih demo | jangan dipakai sebagai kontrol akses produksi sebelum auth dibuat |
| CPU Workers Free 10 ms vs hashing sandi (~20 ms terukur pihak ketiga) | wajib diukur sendiri; keputusan: Workers Paid atau skema alternatif, sebelum auth ditulis |
| Kuota D1/R2/Workers | pencarian di HP, sync kursor `seq`, kompres foto |
| D1 Free 500 MB per database | teks saja tidak mendekati batas; tetap dicek bulanan |

---

## 11. Struktur repositori

```
android/                      aplikasi Android (Kotlin + Compose)
  app/src/main/java/tech/cocok/app/
    domain/                   model, parsing, use case (tanpa Android)
    data/local/               Room: entity, DAO, mapping, repository transaksional
    data/internet/            transport + parser Google Images, unggah foto
    data/visual/              embedding foto di HP
    ui/                       ViewModel, layar, komponen, tema
  app/src/test/               uji JVM + fixture rekaman
  app/src/androidTest/        uji Room, migrasi, embedder, UI
  app/schemas/                skema Room yang diekspor (1.json, 2.json)
api/                          Cloudflare Worker
  src/index.ts                Hono: /v1/health, 404, error envelope
  migrations/0001_init.sql    skema D1 + sync_log + trigger
  wrangler.jsonc              dua lingkungan (uji, produksi)
```

---

## 12. Pertanyaan yang masih terbuka

1. Satu konfirmasi kasir cukup, atau perlu dua sebelum pemilik menyetujui?
2. Kategori mana yang diprioritaskan lebih dulu (lampu, kaca, kampas rem, filter)?
3. Merek/model mobil apa yang paling sering dicari di toko?
4. Sudah ada data di Excel/buku catatan yang bisa diimpor?
5. Berapa kasir/mekanik yang akan memakai, dan di HP seperti apa?
6. Nama aplikasi final?
7. Kapan stok dan harga mulai dibutuhkan?

Asumsi sementara bila belum dijawab: satu konfirmasi, kategori lampu + kaca + kampas, Hilux + Avanza.

---

## 13. Catatan dokumen

README ini adalah satu-satunya dokumen proyek. Sebelumnya ada `PRD.md`, `DESIGN.md`, `ARCHITECTURE.md`, `PLAN.md`, dan `AGENTS.md`; isinya sudah digabung ke sini dan riwayatnya tetap ada di git.

Yang perlu diingat saat mengubah kode:

- `domain/` tetap bebas Android dan jaringan.
- Perubahan skema Room butuh versi baru, migrasi, dan ekspor skema bersama.
- Jangan menambah warna/ukuran langsung di layar — pakai token tema.
- Data contoh tidak boleh masuk ke produksi; nomor part contoh belum diverifikasi.
- Setiap klaim "sudah berjalan" harus punya bukti yang dijalankan, bukan asumsi.
