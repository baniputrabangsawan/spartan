# Handoff: Aplikasi "Cocok" — Analisis, Perbaikan, dan Optimasi

Dokumen ini untuk agent lain yang akan mengaudit dan mengembangkan proyek ini.
Semua angka dan klaim di bawah **diverifikasi dari kode dan perintah yang dijalankan**,
bukan dari rencana. Bagian "Belum ada" adalah fakta, bukan roadmap.

Repositori: `github.com/baniputrabangsawan/spartan` (branch `main`, commit `44d54af`).

---

## 1. Apa aplikasi ini

Aplikasi Android untuk toko suku cadang mobil. Masalah yang dipecahkan: kasir harus
memastikan apakah sebuah barang cocok untuk mobil tertentu, atau bisa diganti barang
lain saat stok habis. Sekarang jawabannya dicari manual di Google/chatbot: lambat,
tanpa nomor part, tanpa tingkat keyakinan, dan tidak tersimpan.

Tujuan produk: jawaban **< 10 detik** dengan keyakinan yang jelas, plus database
milik toko yang makin lengkap sehingga retur karena salah kecocokan turun.

Pengguna: **pemilik** (menetapkan status akhir), **kasir** (pemakai paling sering),
**mekanik** (butuh tampilan paling sederhana). Konteks pemakaian: berdiri, di toko
terang, kadang tangan kotor. Karena itu teks besar, tombol besar, satu pekerjaan per layar.

Stack (dideteksi dari `android/app/build.gradle.kts` dan `api/package.json`):

| Lapisan | Teknologi |
|---|---|
| Aplikasi | Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, minSdk 26 / target 35 |
| Penyimpanan | Room 2.6.1 (versi skema **2**), tanpa FTS |
| Foto | ML Kit text-recognition 16.0.1 (OCR), MediaPipe tasks-vision 1.2.3 (embedding), Coil 2.7.0 |
| Backend | Cloudflare Workers + Hono 4.13.19 + D1, TypeScript 7, wrangler 4 |
| Uji | JUnit 4, AndroidX instrumentation, Compose UI test |

---

## 2. Fitur yang berjalan sekarang

### 2.1 Pencarian di HP

- Input bebas: `stop hilux rebon 2018`. Normalisasi huruf/tanda baca, 4 digit = tahun,
  sinonim toko (`rebon`→Reborn, `avansa`→Avanza), toleransi salah ketik via Levenshtein.
- **Kata mengandung angka dicocokkan persis** — `81560` vs `81150` adalah barang berbeda.
- Nomor part dicari sebagai satu kesatuan lebih dulu, sebelum pemecahan kata.
- Bila mobil disebut: hanya kelompok yang punya kecocokan untuk mobil (dan tahun) itu
  yang boleh muncul. Ini pernah menjadi bug (barang Vigo tampil sebagai PASTI untuk Reborn).
- Tidak ada hasil → tampil pilihan/keadaan kosong, bukan galat.

### 2.2 Label status (aturan inti produk)

| Label | Warna | Ikon | Teks pendamping |
|---|---|---|---|
| PASTI COCOK | hijau `#1E7B34` | centang | "Sudah dicek, aman dipasang" |
| MUNGKIN COCOK | kuning `#7A4F00` | tanda tanya | "Belum dicek. Lihat barangnya dulu." |
| TIDAK COCOK | merah `#B3261E` | silang | "Jangan dipasang" |
| BELUM ADA DATA | abu `#5F6368` | minus | "Belum ada info. Bisa dicari." |

Aturan yang ditegakkan di kode:
- `BELUM_ADA_DATA` adalah **nilai turunan**, tidak pernah disimpan di DB, dan **tidak**
  diperlakukan sama dengan `TIDAK_COCOK`.
- Gabungan status memakai `terendahDiketahui` (mengabaikan "belum ada data" saat
  menggabungkan status yang sudah tercatat) — ini memperbaiki bug label yang menurun.
- "Beda" hanya bila kedua sisi `PASTI` dan grupnya tidak beririsan; selain itu `BELUM TAHU`.

### 2.3 Layar (9 + 1 sub-layar)

| Layar | Berkas | Isi |
|---|---|---|
| Login | `PengaturanScreen.kt:49` | pilih peran + nama. **Demo**, bukan autentikasi |
| Cari (beranda) | `CariScreen.kt` | kotak cari besar, tombol "Cari padanan barang", chip kategori/mobil, riwayat |
| Pencocokan | `MatchScreen.kt` (758 baris) | 3 langkah: sumber → kandidat → banding |
| Hasil | `HasilScreen.kt` | kartu + label, urut PASTI dulu, `tidak_cocok` dilipat, skeleton saat memuat |
| Detail | `DetailScreen.kt` | foto, nomor part asli/merek lain, "Cocok untuk", catatan, tombol konfirmasi |
| Pengganti | `DetailScreen.kt:236` | barang lain di grup yang sama |
| Bandingkan | `BandingkanScreen.kt` | 3 kotak → satu jawaban besar SAMA/BEDA/BELUM TAHU |
| Tambah | `TambahScreen.kt` | tambah barang / mobil / hubungkan |
| Pengaturan | `PengaturanScreen.kt:108` | peran, status sambungan, keluar |
| Cari di internet | `PengaturanScreen.kt:189` | riset semi-manual (teks siap salin → formulir) |

Komponen bersama di `ui/components/`: `TombolUtama`, `TombolGaris`, `Kartu`,
`FotoBarang`, `FotoProduk` (Coil, fallback inisial), `ChipPilihan`, `NomorLangkah`,
`BarisJudul`, `StatusBadge`, `StatusChip`.

### 2.4 Pencocokan barang (fitur utama)

`domain/usecase/MatchParts.kt` memisahkan tiga hal yang tidak boleh dicampur:

```
discover()  → penemuan kandidat (tidak pernah memberi label cocok)
compare()   → perbandingan spesifikasi + pemeriksaan fisik
record()    → bukti pemilik (satu-satunya sumber label terverifikasi)
```

- Kandidat datang dari: grup barang yang sama, pencarian katalog, kemiripan foto lokal,
  dan hasil gambar internet.
- Perbandingan menampilkan dua identitas berdampingan, baris `sama / beda / belum ada`
  (spesifikasi bisa diisi langsung), lalu daftar **pemeriksaan fisik** untuk data kosong
  ("ukur jarak baut", "cocokkan soket", dst).
- **Spesifikasi yang sama tidak menaikkan hasil** menjadi "pengganti"; nilainya tetap
  `Kandidat — belum terverifikasi`.
- Label `Barang sama` / `Pengganti terverifikasi` hanya dari `MatchEvidence` pemilik yang
  terikat ke mobil + tahun + varian. Kasir ditolak; konteks tidak lengkap ditolak; bukti
  tidak bocor ke mobil lain.
- Item hasil edit atau dari internet **tidak mewarisi** identitas katalog
  (`catalogPartId` dikosongkan) tetapi tetap menyimpan `linkedCatalogPartId` supaya
  spesifikasi hasil edit masih bisa disimpan.

### 2.5 Foto dan internet

- **OCR lokal** (`PembacaFoto`): kamera/galeri → teks → nomor part. Foto tidak diunggah
  untuk OCR; salinan privat di `cacheDir`.
- **Kemiripan bentuk lokal** (`LocalVisualMatcher`): MediaPipe ImageEmbedder +
  MobileNet-v3-small bawaan (`assets/mobilenet_v3_small.tflite`, Apache-2.0,
  `noCompress`). Skor kosinus = kemiripan bentuk, **bukan** peluang cocok. Tanpa foto di
  katalog → menyatakan belum ada indeks, tidak mengarang hasil.
- **Gambar internet** (`GoogleKeywordSearch`): keyword → Google Images (`q`, `udm=2`) →
  respons diparse (`GoogleImageParser`) → kartu Compose native dengan foto, judul,
  sumber, tombol Bandingkan/Buka sumber. WebView sementara tanpa tampilan, tanpa jembatan
  JS ke Room, dihancurkan setelah selesai.
- **Kemiripan foto internet** (`GoogleVisualSearch`): unggah foto hanya setelah
  **persetujuan per foto**, dibatasi HTTPS + JPEG tanpa metadata + batas ukuran.
  CAPTCHA/persetujuan Google tidak pernah dilewati.

### 2.6 Penyimpanan lokal

Room v2, transaksi, migrasi eksplisit 1→2 (`specifications` pada `parts`, tabel
`match_evidence`). Data contoh hanya masuk sekali lewat flag inisialisasi; katalog yang
dikosongkan tetap kosong. `PRAGMA foreign_key_check` dijalankan sebelum commit agar
rollback bersih dan koneksi SQLite tidak tertinggal dalam transaksi gagal.

Tabel: `vehicles`, `part_categories`, `parts`, `fitment_groups`, `group_parts`,
`fitments`, `synonyms`, `confirmations`, `match_evidence`, `catalog_metadata`.

---

## 3. Backend: apa yang nyata dan apa yang tidak

`api/src/index.ts` **hanya** punya `GET /v1/health` (cek skema D1 + akses R2) dan
`notFound`/`onError`. Tidak ada auth, tidak ada rate limit, tidak ada rute data.

Skema D1 sudah ada (`0001_init.sql`, 15 tabel, 27 trigger `sync_log`) untuk: `users`,
`sessions`, `sources`, `change_log`, `usage_counters`, `sync_log`. Jadi database siap,
API belum.

Status deploy yang terverifikasi:
- Worker produksi & staging ter-deploy dengan binding D1 saja.
- `/v1/health` remote menjawab **`503 storage_unavailable`** karena R2 belum aktif
  (aktivasi meminta metode pembayaran). Dengan D1+R2 lokal: `200`.
- Rute data/foto → `404`. Katalog produksi **tidak** terbuka ke publik.

Android **belum memanggil** Worker sama sekali. Semua data lokal.

---

## 4. Kondisi teknis terukur

| Metrik | Angka | Cara ukur |
|---|---|---|
| Cold start | 596–728 ms | `am start -W`, 3× |
| Pencarian 500 barang | p50 4,6 ms | `SearchPerformanceTest` (JVM) |
| Pencarian 20.000 barang | **165 ms** | idem (dulu 1.652 ms) |
| Bandingkan 20.000 barang | **11 ms** | idem (dulu 700 ms) |
| Daftar pengganti 20.000 | **< 150 ms** | `PathCostTest` (dulu 1.466 ms) |
| Memori (TOTAL PSS) | 102,8 MB | `dumpsys meminfo` |
| APK debug | 139 MB | `ls` |
| Uji JVM | **54 lulus** | `./gradlew :app:testDebugUnitTest` |
| Uji instrumentasi | **20 lulus** | `connectedDebugAndroidTest` (Infinix X6885, Android 16) |
| Worker | typecheck + build lulus | `npm run typecheck`, `npm run build` |

Perbaikan performa di commit `44d54af`: indeks id & keanggotaan grup dibuat sekali per
`Catalog` (immutable, `by lazy`), fitment diindeks sekali per pencarian, dan pencarian
hasil dipindah ke `Dispatchers.Default` + skeleton (sebelumnya memblokir composition).

---

## 5. Batasan nyata (jangan diasumsikan sudah ada)

1. **Tidak ada autentikasi.** Pemilihan peran di layar Login adalah kontrol demo; siapa
   pun bisa memilih "Pemilik". Semua aturan peran ditegakkan hanya di klien.
2. **Tidak ada sinkronisasi antar-HP.** Tidak ada outbox, tidak ada `SyncWorker`.
3. **Tidak ada foto permanen.** R2 belum aktif; foto hanya cache sementara.
4. **Tidak ada uji API** (`api/test` tidak ada), tidak ada CI.
5. **Build rilis belum dikonfigurasi**: tidak ada `isMinifyEnabled`, `shrinkResources`,
   atau `proguard-rules.pro`. Release masih unminified.
6. Kamera fisik, rotasi layar, dan CAPTCHA Google **belum diuji ulang** pada build ini.
7. Tidak ada mode gelap (token warna sudah disiapkan agar mudah ditambah).
8. `README.md` adalah satu-satunya dokumen proyek (PRD/DESIGN/ARCHITECTURE sudah
   digabung ke sana; riwayatnya ada di git).

---

## 6. Peta kode

```
android/app/src/main/java/tech/cocok/app/
  CocokApplication.kt     DI manual (lazy database + repository), tanpa Hilt
  MainActivity.kt         edge-to-edge → CocokTheme { CocokApp() }
  domain/                 murni Kotlin, tanpa Android/jaringan
    model/                Status, Vehicle, Part, FitmentGroup, Fitment, Catalog,
                          SearchHit, ItemMatching (MatchItem/MatchEvidence)
    search/               TextMatch (Levenshtein, angka persis), QueryParser, HitOrdering
    usecase/              SearchParts, CompareParts, MatchParts
  data/local/             Room: entities, DAO, converters, CatalogRows, repository transaksional
  data/internet/          GoogleKeywordSearch (WebView + parser), GoogleVisualSearch (unggah)
  data/visual/            LocalVisualMatcher (MediaPipe embedding)
  ui/                     CocokApp (navigasi overlay + BackHandler), CocokViewModel,
                          MatchViewModel, screens/, components/, theme/
api/src/index.ts          Hono: /v1/health, 404, error envelope
api/migrations/0001_init.sql
```

Berkas terbesar (kandidat dipecah bila menyentuh UI lagi):
`MatchScreen.kt` 758, `GoogleKeywordSearch.kt` 392, `TambahScreen.kt` 391,
`BandingkanScreen.kt` 345, `CocokApp.kt` 339, `CocokViewModel.kt` 324.

### Alur data

```
Compose screen → ViewModel (StateFlow) → use case domain murni → CatalogRepository → Room
```

- Perubahan katalog baru dipublikasikan ke UI **setelah** transaksi commit.
- `domain/` tidak boleh mengimpor Android atau jaringan (aturan yang sudah dijaga).

---

## 7. Perintah yang ada (jangan diganti)

```bash
# Android (dari android/)
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest                  # 54 uji JVM
./gradlew :app:connectedDebugAndroidTest          # 20 uji perangkat (butuh HP/emulator)
./gradlew :app:lintDebug

# Worker (dari api/)
npm ci
npm run typecheck
npm run build          # wrangler deploy --dry-run
npm run migrate:local
npm run dev
# JANGAN jalankan tanpa izin: deploy, deploy:staging, migrate:staging, migrate:production
```

Catatan operasional: `connectedDebugAndroidTest` lewat Gradle bisa bertabrakan bila
perangkat dipakai proyek lain (pernah menjalankan tes proyek lain dan menggantung).
Jalur yang terbukti stabil: `adb shell am instrument -w -e class <FQCN> tech.cocok.app.test/androidx.test.runner.AndroidJUnitRunner`.

---

## 8. Temuan yang sudah diperbaiki (jangan diulang)

| Cacat | Akar masalah | Perbaikan |
|---|---|---|
| Spesifikasi hasil edit hilang | `editItem` mengosongkan `catalogPartId`, tapi tombol simpan bergantung field itu; `simpanSpesifikasi(null)` no-op senyap | `linkedCatalogPartId` (alamat baris, tanpa kepercayaan) + gagal simpan melaporkan masalah |
| Barang Vigo tampil PASTI untuk Reborn | grup tanpa fitment untuk mobil yang disebut tetap ditampilkan | wajib ada fitment untuk mobil yang disebut |
| Pengganti berlabel BELUM ADA DATA | "belum ada data" menurunkan status yang sudah tercatat | `Status.terendahDiketahui` |
| Pencarian 1,65 s @20k | filter fitment berulang per grup + lookup id linear | indeks sekali per pencarian & per Catalog |
| Bandingkan 700 ms @20k | rescan `groupParts` per grup | satu lintasan + indeks |
| Pengganti 1,4 s @20k | `groups.any { groupParts.any { } }` | `grupDariPart` |
| Hasil pencarian memblokir UI | `vm.cari()` di dalam `remember` saat composition | `LaunchedEffect` + `Dispatchers.Default` + skeleton |
| Layar Pengaturan tanpa jalan kembali | hanya tombol "Keluar" | tombol kembali + `BackHandler` |
| Nomor part gagal cocok karena OCR (O/0) | perbandingan persis tanpa normalisasi | koreksi karakter OCR, hanya bila hasil tunggal |

---

## 9. Kandidat area perbaikan (untuk dianalisis ulang, bukan instruksi)

Berikut yang **terbukti dari kode**, dengan tingkat keparahan menurut dampak ke pemakaian:

**Tinggi**
- **Autentikasi & otorisasi belum ada.** Aturan "hanya pemilik" saat ini hanya kosmetik;
  siapa pun yang memilih peran Pemilik bisa menetapkan `pasti`. Ini risiko produk, bukan
  bug kecil. Perlu keputusan lingkup (login sendiri vs Cloudflare Access vs PIN perangkat).
- **Tidak ada sinkronisasi.** Dua HP = dua kebenaran berbeda. Seluruh nilai "database
  toko" bergantung pada sinkronisasi yang belum ada.
- **Build rilis belum dioptimalkan** (tanpa R8/shrink): APK 139 MB (debug) akan jauh lebih
  besar dari perlu; juga belum ada Baseline Profile.

**Sedang**
- **`CocokViewModel` 324 baris** menangani katalog, peran, riset, riwayat, dan pesan.
  Bisa dipecah per tanggung jawab bila disentuh lagi.
- **`MatchScreen` 758 baris** mencampur 3 langkah + formulir + dialog. Kandidat dipecah
  per langkah komponen.
- **`GoogleKeywordSearch` bergantung pada format web Google** yang tidak stabil
  (parser 392 baris). Perlu strategi bila format berubah; saat ini hanya pesan error.
- **Tidak ada uji API** meski skema D1 sudah lengkap — rute apa pun yang ditambahkan
  nanti tidak punya jaring pengaman.
- **Kode mati**: `Catalog.statusGrup`, `Catalog.kendaraanUntukGrup`,
  `CocokViewModel.hasilUntuk`, `BarisRapat`, `KolomBerkas` (0 pemakai).
- **Riwayat pencarian & sesi hanya di memori**, hilang saat proses mati.
- **Foto cache tidak dipangkas**: `cacheDir/foto` tumbuh sampai sistem membuangnya.

**Rendah**
- `NumberLangkah`, `BarisJudul` dipakai sedikit; konsistensi token spacing belum
  sepenuhnya lewat `Dimensi`.
- `DetailScreen` memakai `hit.catatan!!` setelah `isNullOrBlank` (aman tapi rapuh).
- `hasScrollToNodeAction` dan beberapa import uji tidak terpakai.

**Optimasi yang masuk akal (dengan bukti pendukung)**
- Baseline Profile + R8/shrink untuk cold start & ukuran.
- Index Room pada `parts(partNumber)`, `fitments(vehicleId)`, `group_parts(groupId)`
  sudah di model; pastikan `@Index` benar-benar ada sebelum katalog nyata tumbuh.
- `derivedStateOf` untuk daftar hasil terfilter di layar hasil.
- Coil: batas memori/disk cache eksplisit (sekarang default).
- Hilangkan `distinctBy` berlapis di `HitOrdering` bila katalog besar terukur lambat lagi.

---

## 10. Pertanyaan produk yang belum diputuskan

1. Satu konfirmasi kasir cukup, atau perlu dua sebelum pemilik menyetujui?
2. Kategori prioritas (lampu / kaca / kampas / filter)?
3. Mobil yang paling sering dicari di toko (untuk data awal)?
4. Sudah ada data Excel/buku yang bisa diimpor?
5. Nama aplikasi final?
6. Kapan stok & harga mulai dibutuhkan (mempengaruhi skema)?

---

## 11. Cara memverifikasi pekerjaan Anda sendiri

1. `./gradlew :app:testDebugUnitTest` → harus 54 lulus (jumlah naik bila Anda menambah).
2. `./gradlew :app:connectedDebugAndroidTest` **atau** jalur `am instrument` per kelas.
3. Untuk perubahan performa: jalankan `SearchPerformanceTest` dan `PathCostTest`;
   keduanya gagal bila ada pemindaian kuadratik yang tidak sengaja masuk.
4. Untuk perubahan UI: uji Compose yang ada memeriksa teks nyata
   (`KeywordCompareFlowTest`, `MatchScreenTest`, `KeywordPhotoGridTest`,
   `FotoProdukTest`). Model agen yang tidak mendukung input gambar tidak dapat menilai
   tampilan secara visual — nyatakan batas itu, jangan mengarang.
5. Worker: `npm run typecheck` + `npm run build`.

Jangan menambah AGENTS.md, CI, atau berkas metadata hanya untuk menaikkan skor alat.
Jangan menulis rahasia/token ke berkas yang ter-commit.
