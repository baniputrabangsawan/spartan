# DESIGN: Tampilan Aplikasi "Cocok"

Dokumen ini menjelaskan bagaimana aplikasi harus **terlihat dan terasa**. Ditulis agar mudah dipahami oleh pemilik toko, kasir, dan mekanik, bukan hanya oleh pembuat aplikasi.

---

## 1. Prinsip: satu kalimat

> **Seperti papan tulis di toko: besar, jelas, jawabannya langsung kelihatan.**

Lima aturan yang tidak boleh dilanggar:

1. **Tulisan besar.** Bisa dibaca sambil berdiri, di bawah lampu toko, tanpa kacamata.
2. **Satu layar, satu pekerjaan.** Tidak ada layar yang penuh pilihan.
3. **Jawaban dulu, penjelasan belakangan.** Hal terpenting (Cocok atau tidak) ada paling atas.
4. **Bahasa sehari-hari.** Tidak ada istilah teknis.
5. **Jangan mengandalkan warna saja.** Setiap warna selalu disertai kata dan tanda.

---

## 2. Kata-kata yang dipakai

Aplikasi memakai kata yang biasa diucapkan di toko.

| Jangan pakai | Pakai |
|---|---|
| Kelompok kecocokan / fitment | **Barang yang sama** |
| Verifikasi | **Sudah dicek** |
| OEM | **Asli (original)** |
| Aftermarket | **Merek lain** |
| Sinkronisasi | **Memperbarui data** |
| Query / filter | **Cari** |
| Database | **Data** |
| Error | **Ada masalah** + apa yang harus dilakukan |

Contoh pesan masalah: *"Tidak ada internet. Data yang tersimpan di HP tetap bisa dipakai."*

---

## 3. Label hasil (bagian terpenting)

Setiap hasil punya **satu label** yang besar. Label memakai **warna + ikon + kata**, jadi tetap jelas walau orang buta warna atau layar terkena cahaya terang.

| Label | Warna | Ikon | Teks pendamping |
|---|---|---|---|
| **PASTI COCOK** | Hijau | Centang | "Sudah dicek, aman dipasang" |
| **MUNGKIN COCOK** | Kuning | Tanda tanya | "Belum dicek. Lihat barangnya dulu." |
| **TIDAK COCOK** | Merah | Silang | "Jangan dipasang" |
| **BELUM ADA DATA** | Abu-abu | Tanda minus | "Belum ada info. Bisa dicari." |

Aturan: label **tidak pernah** lebih kecil dari teks nama barang.

---

## 4. Warna

Terang dan kontras tinggi, karena dipakai di toko yang terang.

| Fungsi | Warna | Kode |
|---|---|---|
| Warna utama (tombol utama) | Biru tua | `#1B5E9B` |
| Latar halaman | Putih | `#FFFFFF` |
| Teks utama | Hitam kebiruan | `#1A1C1E` |
| Teks pendamping | Abu tua | `#44474E` |
| Pasti (teks / latar) | `#1E7B34` / `#E6F4EA` |
| Mungkin (teks / latar) | `#7A4F00` / `#FFF3CD` |
| Tidak cocok (teks / latar) | `#B3261E` / `#FDE7E9` |
| Belum ada data (teks / latar) | `#5F6368` / `#EEF0F2` |

Cukup satu warna utama. Tidak ada gradasi atau hiasan.

---

## 5. Huruf dan ukuran

Satu jenis huruf yang bersih (misalnya Roboto atau Inter).

| Fungsi | Ukuran | Tebal |
|---|---|---|
| Judul layar | 24 sp | Tebal |
| Nama barang | 22 sp | Tebal |
| Label hasil | 20 sp | Tebal, huruf kapital |
| Teks biasa | 18 sp | Normal |
| Teks kecil (catatan) | 16 sp | Normal |

**Tidak ada teks di bawah 16 sp.** Aplikasi mengikuti pengaturan ukuran huruf HP jika pengguna membesarkannya.

---

## 6. Tombol dan jarak

- Tinggi tombol minimal **56 dp** (lebih besar dari standar), lebar penuh untuk tombol utama.
- Jarak antar tombol minimal **12 dp** agar tidak salah ketuk dengan jari berminyak.
- Tombol selalu **ada tulisannya**, bukan hanya ikon.
- Maksimal **satu tombol utama** per layar (biru penuh). Tombol lain berupa garis tepi.
- Tombol berbahaya (hapus) berwarna merah dan **selalu meminta konfirmasi**.

---

## 7. Menu bawah

Hanya **tiga** tombol di bawah, selalu terlihat:

```
┌───────────────────────────────────────┐
│   🔍 CARI     ⇄ BANDINGKAN   ＋ TAMBAH │
└───────────────────────────────────────┘
```

Pengaturan dan akun ada di ikon di pojok kanan atas (hanya pemilik yang butuh sering).

---

## 8. Layar-layar

Gambar di bawah hanya sketsa kasar agar mudah dibayangkan.

### 8.1 Layar CARI (layar utama)

```
┌─────────────────────────────┐
│  Cocok                   ⚙  │
│                             │
│  Cari barang atau mobil     │
│ ┌─────────────────────────┐ │
│ │ 🔍 Contoh: stop hilux   │ │
│ │    rebon 2018           │ │
│ └─────────────────────────┘ │
│                             │
│  Yang sering dicari         │
│ ┌────────┐ ┌────────┐       │
│ │ Lampu  │ │ Kampas │       │
│ │  stop  │ │  rem   │       │
│ └────────┘ └────────┘       │
│ ┌────────┐ ┌────────┐       │
│ │  Kaca  │ │ Filter │       │
│ └────────┘ └────────┘       │
│                             │
│  Terakhir dicari            │
│  • Stop Avanza 2015         │
│                             │
│ 🔍 CARI   ⇄ BANDINGKAN  ＋  │
└─────────────────────────────┘
```

- Kotak cari besar di atas, langsung siap mengetik.
- Salah ketik tetap dimengerti ("rebon", "avansa").
- Jika mobil atau tahun belum disebut, aplikasi bertanya dengan **pilihan yang bisa diketuk**, bukan meminta mengetik lagi.

### 8.1b Pencarian gambar dan foto

- Pencarian keyword dapat dibuka langsung dari layar Cari, tanpa memilih foto.
- Ambil foto dengan kamera HP atau pilih galeri untuk membaca nomor part/kata lewat OCR lokal. Isian bisa diedit; berkas foto tidak diunggah.
- **Gambar internet:** grid kartu Compose berisi thumbnail nyata, judul, nama situs, dan tautan sumber dari respons Google Images. Tidak menampilkan halaman Google atau Lens sebagai hasil.
- Loading, hasil kosong, kegagalan jaringan/format, dan gambar yang gagal dimuat diberi pesan yang jelas; kegagalan pencarian menyediakan “Coba lagi”. CAPTCHA/persetujuan Google tidak dilewati.
- **Data toko:** cocokkan nomor part dan konteks mobil/tahun dengan katalog lokal. Koreksi OCR hanya menghasilkan kandidat tunggal dengan peringatan; pencarian ketik biasa tidak mengubah huruf/angka nomor part.
- Internet tidak menghasilkan label PASTI COCOK. Bukti kecocokan berasal dari katalog/konfirmasi toko; nomor part yang sama belum menjamin cocok untuk setiap mobil.
- Kartu membuka tautan sumber HTTPS asli di aplikasi/peramban HP. Tombol “Cari di Shopee” adalah pencarian keyword, bukan tautan produk hasil tebakan.
- Parser respons web Google bukan API resmi dan formatnya dapat berubah. Foto berada di cache privat aplikasi, bukan R2; belum menjadi arsip foto permanen.

### 8.1c Layar Pencocokan barang

Tiga langkah bernomor yang jelas; satu tombol utama per langkah.

```
┌─────────────────────────────────────┐
│ ←  Cari padanan barang              │
│                                     │
│ Masukkan satu barang lewat foto     │
│ atau kata kunci.                    │
│ ┌─────────────────────────────────┐ │
│ │ Nama barang, nomor part, mobil  │ │
│ └─────────────────────────────────┘ │
│ [ Ambil foto barang ]               │
│ [ Pilih dari galeri ]               │
│ [        CARI KANDIDAT        ]     │
│                                     │
│ Atau mulai dari data toko           │
│ ┌─────────────────────────────────┐ │
│ │ Lampu stop kiri asli   PASTI    │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

- **Sumber** boleh dari foto, kata kunci, atau barang yang sudah ada di data toko. Tidak ada klaim kecocokan di langkah ini.
- **Kandidat** menampilkan alasan tiap kartu (`kemiripan bentuk 62%`, `grup barang sumber`, `dari pencarian katalog`) dan tombol **Bandingkan** pada setiap kartu.
- **Bandingkan** menampilkan dua identitas berdampingan, baris `sama / beda / belum ada`, lalu daftar **yang masih perlu diperiksa** untuk data kosong.
- Label hasil: **Barang sama**, **Pengganti terverifikasi**, **Bukan pengganti**, **Ada perbedaan**, **Kandidat — belum terverifikasi**. Hanya dua label pertama bisa berasal dari catatan pemilik; sisanya bukan kepastian.
- Foto tanpa tulisan yang tidak punya pasangan di indeks foto katalog ditampilkan sebagai keterangan jujur, bukan hasil tebakan. Unggah foto ke layanan gambar meminta persetujuan per foto.
- Tombol pemilik (catat verifikasi) hanya muncul untuk pemilik dan hanya aktif setelah mobil, tahun, varian, serta catatan pemeriksaan terisi. Kasir melihat penjelasan, bukan tombol yang dinonaktifkan tanpa alasan.

### 8.2 Layar HASIL

```
┌─────────────────────────────┐
│ ←  Lampu stop Hilux Reborn  │
│     2018                    │
│                             │
│ ┌─────────────────────────┐ │
│ │ [foto]  Stop lamp kiri  │ │
│ │         No. 81560-0K..  │ │
│ │ ✔ PASTI COCOK           │ │
│ │ Sudah dicek, aman       │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ [foto]  Stop lamp merek │ │
│ │         lain            │ │
│ │ ? MUNGKIN COCOK         │ │
│ │ Lihat barangnya dulu    │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

- Satu kartu per barang, foto di kiri, label di bawahnya.
- Urutan: **Pasti** di atas, lalu **Mungkin**.

### 8.3 Layar DETAIL BARANG

```
┌─────────────────────────────┐
│ ←  Stop lamp kiri           │
│ ┌─────────────────────────┐ │
│ │        [foto besar]     │ │
│ └─────────────────────────┘ │
│ ✔ PASTI COCOK               │
│                             │
│ Nomor part                  │
│  Asli:      81560-0K...     │
│  Merek lain: ABC-123        │
│                             │
│ Cocok untuk                 │
│  • Hilux Reborn 2016–2020   │
│  • Fortuner 2016–2020       │
│                             │
│ Catatan                     │
│  Hanya untuk model standar  │
│                             │
│ [ BARANG SAMA (PENGGANTI) ] │
│ [ ✔ SUDAH DIPAKAI, COCOK ]  │
│ [ ✖ TERNYATA TIDAK COCOK ]  │
└─────────────────────────────┘
```

- Tombol konfirmasi ada di bawah, besar dan jelas.
- "Barang yang sama" menampilkan pengganti jika stok habis.

### 8.4 Layar BANDINGKAN

Dibuat seperti mengisi tiga kotak, satu per satu.

```
┌─────────────────────────────┐
│  Bandingkan                 │
│                             │
│  1. Barang apa?             │
│ [ Lampu stop            ▼ ] │
│                             │
│  2. Mobil pertama           │
│ [ Hilux Reborn   2018   ▼ ] │
│                             │
│  3. Mobil kedua             │
│ [ Hilux Vigo     2012   ▼ ] │
│                             │
│ [        BANDINGKAN       ] │
└─────────────────────────────┘
```

Hasil ditampilkan sebagai satu jawaban besar:

```
┌─────────────────────────────┐
│       ✖ BEDA                │
│  Barangnya tidak sama.      │
│                             │
│  Hilux Reborn 2018          │
│   → No. 81560-0K...         │
│  Hilux Vigo 2012            │
│   → No. 81560-0K... (lain)  │
└─────────────────────────────┘
```

Jawaban bisa **SAMA**, **BEDA**, atau **BELUM TAHU** (jika belum ada data, bukan dianggap beda).

### 8.5 Layar TAMBAH

Berisi tiga pilihan besar: **Tambah barang**, **Tambah mobil**, **Hubungkan barang dengan mobil**. Setiap formulir hanya menanyakan yang perlu. Tombol **Cari di internet** tampil di sini (tahap berikutnya).

---

## 9. Kondisi khusus

| Kondisi | Yang ditampilkan |
|---|---|
| Sedang mencari | Lingkaran berputar + tulisan "Sedang mencari…" |
| Tidak ada hasil | "Belum ada data." + tombol **Cari di internet** dan **Tambah sendiri** |
| Tidak ada internet | Pita kuning di atas: "Tidak ada internet. Data di HP tetap bisa dipakai." |
| Berhasil disimpan | Pesan hijau singkat: "Tersimpan." |
| Ada masalah | Pesan jelas + satu tombol "Coba lagi" |

---

## 10. Kemudahan untuk semua orang

- Kontras teks terhadap latar minimal 4.5 : 1.
- Tombol dan teks tidak bergantung pada warna saja.
- Area ketuk minimal 48 dp (kita pakai 56 dp).
- Mendukung ukuran huruf besar dari pengaturan HP.
- Bisa dipakai dengan satu tangan: tombol penting di bagian bawah layar.
- Tidak ada gerakan atau animasi yang mengganggu. Perpindahan layar halus dan cepat.

---

## 11. Gaya visual

**Sederhana tapi modern.** Artinya:

- Kartu putih dengan sudut membulat dan bayangan sangat tipis.
- Banyak ruang kosong, tidak padat.
- Ikon garis sederhana dari satu set ikon (misalnya Material Symbols).
- Foto barang selalu persegi, latar putih.
- Tidak ada iklan, banner, atau hiasan yang tidak perlu.

Dasar teknis: **Material 3** (bawaan Android), dengan ukuran dan warna disesuaikan seperti di atas.

---

## 12. Referensi tampilan (Mobbin)

> **Status: belum diisi.** Mobbin belum disambungkan saat dokumen ini dibuat, jadi tidak ada referensi yang diambil. Bagian ini sengaja dibiarkan kosong supaya tidak ada referensi karangan.

Setelah Mobbin tersambung, cari dengan kata kunci berikut dan catat hasilnya di tabel.

| Yang dicari | Kata kunci pencarian | Yang dilihat | Referensi (isi nanti) | Catatan |
|---|---|---|---|---|
| Layar cari utama | search, home, catalog | Kotak cari besar, saran cepat, riwayat | | |
| Hasil dengan label status | search results, status badge, list | Kartu dengan foto dan label jelas | | |
| Detail produk | product detail, spare parts | Foto besar, daftar spesifikasi | | |
| Pilih mobil dan tahun | vehicle selector, garage, year picker | Cara memilih mobil dengan cepat | | |
| Perbandingan dua barang | compare, side by side | Tampilan jawaban sama/beda | | |
| Keadaan kosong dan error | empty state, error, offline | Pesan ramah dengan satu tombol | | |
| Formulir tambah data | form, add item, input | Formulir pendek dan jelas | | |

**Yang dicari dari referensi:** layout sederhana, teks besar, satu tombol utama, label status yang jelas. **Yang dihindari:** layar penuh pilihan, ikon tanpa tulisan, animasi berlebihan.

---

## 13. Uji bersama pengguna asli

Sebelum desain dianggap selesai, uji dengan mekanik dan ayah pemilik:

1. Berikan HP dan minta: *"Cari lampu stop Hilux Reborn 2018."* Catat apakah berhasil tanpa dibantu.
2. Minta: *"Apakah kaca depan Hilux 2012 sama dengan 2020?"* (mode Bandingkan).
3. Minta: *"Barang ini habis, cari penggantinya."*
4. Tanyakan: *"Menurut Bapak, label hijau, kuning, dan merah artinya apa?"*

**Lulus** jika tiga tugas pertama berhasil tanpa bantuan dan arti label dijawab dengan benar. Jika ada yang bingung, ubah desainnya, bukan penggunanya.
