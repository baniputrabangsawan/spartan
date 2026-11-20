# PRD: Aplikasi Cek Kecocokan Suku Cadang

**Nama kerja:** "Cocok" (sementara, bisa diganti)
**Platform:** Android
**Status:** Draf 1
**Terakhir diperbarui:** 17 November 2026

---

## 1. Latar belakang

Di toko suku cadang, kasir dan pemilik sering kesulitan memastikan apakah sebuah barang cocok untuk mobil tertentu, atau bisa diganti dengan barang lain ketika stok habis. Contohnya:

- Lampu stop Hilux Reborn sama atau tidak dengan Hilux yang baru?
- Kampas rem depan Avanza bisa dipakai di mobil lain?
- Kaca Hilux tahun 2005-2015 sama atau tidak dengan tahun 2020?

Sekarang jawabannya dicari manual di Google atau lewat chatbot. Hasilnya lambat, tidak menyertakan nomor part, tidak jelas seberapa yakin jawabannya, dan tidak tersimpan untuk dipakai lagi.

## 2. Tujuan

1. Kasir dapat menjawab pertanyaan kecocokan barang **dalam waktu kurang dari 10 detik**.
2. Setiap jawaban menunjukkan **tingkat keyakinan** yang jelas (pasti atau mungkin).
3. Toko memiliki **database kecocokan sendiri** yang makin lengkap seiring pemakaian.
4. Jumlah barang **salah beli atau retur** karena salah kecocokan berkurang.

## 3. Bukan tujuan (untuk versi pertama)

- Bukan pengganti sistem kasir atau pembukuan.
- Tidak mengelola stok, harga, dan pembelian (baru dipertimbangkan di tahap lanjut).
- Tidak menjual barang secara online.
- Tidak berjalan di iPhone.

## 4. Pengguna

| Pengguna | Kebutuhan utama | Catatan |
|---|---|---|
| **Pemilik** | Memastikan data benar, mengubah status menjadi "pasti" | Satu atau dua orang |
| **Kasir** | Cari cepat, lihat pengganti, usulkan kecocokan | Pemakai paling sering |
| **Mekanik** | Cek barang untuk mobil pelanggan | Bukan orang teknologi, butuh tampilan sangat sederhana |

Semua pengguna harus bisa memakai aplikasi **tanpa pelatihan panjang**. Target: orang yang baru melihat aplikasi bisa mencari barang dalam 1 menit.

## 5. Istilah penting

| Istilah | Arti |
|---|---|
| **Kelompok barang yang sama** | Kumpulan barang yang bentuk dan ukurannya identik dan bisa saling menggantikan |
| **Pasti cocok** | Nomor part sama, atau sudah dikonfirmasi pemilik setelah terbukti terpasang |
| **Mungkin cocok** | Ditemukan dari internet atau kemiripan, belum diperiksa |
| **Tidak cocok** | Sudah dipastikan berbeda |
| **Belum ada data** | Aplikasi belum tahu |

## 6. Kebutuhan fungsional

### P0: wajib ada di versi pertama

| Kode | Fitur | Penjelasan |
|---|---|---|
| F1 | Pencarian toleran salah ketik | Ketik "stop hilux rebon 2018" tetap menemukan Hilux Reborn. Mendukung istilah sehari-hari (rebon/reborn, cakrem/cakram, avansa/avanza). |
| F2 | Hasil dengan label | Setiap hasil berlabel Pasti / Mungkin / Tidak cocok / Belum ada data, beserta warna dan ikon. |
| F3 | Detail barang | Foto, nomor part (asli dan merek lain yang setara), catatan khusus (misalnya "hanya 4x4"). |
| F4 | Cari pengganti | Dari satu barang, tampilkan barang lain di kelompok yang sama. |
| F5 | Mode Bandingkan | Pilih barang, lalu dua mobil (dengan tahun), dan aplikasi menjawab Sama / Beda / Belum tahu. |
| F6 | Konfirmasi dan koreksi | Kasir menekan "Sudah dipakai, cocok" atau "Ternyata tidak cocok". Pemilik menetapkan status akhir. |
| F7 | Tambah dan ubah data manual | Tambah mobil, barang, nomor part, foto, dan hubungan kecocokan. |
| F8 | Login dan peran | Pemilik dan kasir punya hak berbeda. |
| F9 | Data tersimpan di HP | Hasil pencarian yang sering dipakai tetap bisa dibuka saat sinyal lemah. |

### P1: segera setelah versi pertama stabil

| Kode | Fitur | Penjelasan |
|---|---|---|
| F10 | Cari di internet (semi-manual) | Tombol menyiapkan pertanyaan siap salin untuk chatbot, hasilnya ditempel ke formulir terstruktur dan disimpan sebagai "mungkin cocok". |
| F11 | Impor dari Excel/CSV | Memasukkan data yang sudah ada. |
| F12 | Ekspor ke CSV | Cadangan data. |
| F13 | Riwayat perubahan | Siapa mengubah apa dan kapan. |

### P2: tahap lanjut

| Kode | Fitur |
|---|---|
| F14 | Pencarian internet otomatis (pencarian web + AI di server) |
| F15 | Pemindai barcode atau nomor part dengan kamera |
| F16 | Stok, harga, dan lokasi rak, dengan saran pengganti yang tersedia |
| F17 | Pencarian dengan foto barang |

## 7. Aturan bisnis penting

1. **Hasil dari internet tidak pernah langsung berlabel "pasti".** Selalu mulai dari "mungkin".
2. **Hanya pemilik yang bisa menetapkan status "pasti"** atau "tidak cocok". Kasir hanya bisa mengusulkan dan memberi konfirmasi.
3. **Data yang sudah dikonfirmasi pemilik menang** atas data dari internet jika keduanya bertentangan.
4. **Belum ada data bukan berarti beda.** Jika aplikasi belum punya informasi, jawabannya "Belum ada data", bukan "Beda".
5. **Model baru** (misalnya Hilux 2026) otomatis berstatus "belum diperiksa" sampai ada konfirmasi.
6. Hasil "mungkin" yang hanya dari satu sumber menampilkan peringatan: **"Cek barang langsung sebelum dijual."**
7. Setiap perubahan status tercatat (siapa, kapan, alasan).
8. Kecocokan dicatat **per jenis barang**. Kaca depan, kaca pintu, dan lampu stop untuk mobil yang sama bisa punya jawaban berbeda.
9. Kecocokan dicatat dengan **rentang tahun** dan **varian** (kabin, mesin, 2WD/4WD, manual/matic) jika berpengaruh.

## 8. Alur utama

**Cari barang**
Kasir mengetik → aplikasi memperbaiki ejaan dan mengenali mobil, tahun, dan barang → jika kurang jelas, tampil pilihan yang bisa diketuk → hasil muncul dengan label → kasir membuka detail.

**Barang tidak ditemukan**
Aplikasi menampilkan "Belum ada data" → kasir atau pemilik menekan "Cari di internet" → hasil disimpan sebagai "mungkin" → setelah barang terpasang dan terbukti, dikonfirmasi menjadi "pasti".

**Bandingkan**
Pilih jenis barang → pilih mobil A dan tahun → pilih mobil B dan tahun → jawaban Sama / Beda / Belum tahu, disertai nomor part.

## 9. Kebutuhan non-fungsional

| Aspek | Target |
|---|---|
| Kecepatan | Hasil pencarian tampil dalam 1 detik untuk data yang sudah ada |
| Mudah dipakai | Teks besar, tombol besar, bahasa sehari-hari (lihat DESIGN.md) |
| Offline | Data yang sering dipakai tetap terbuka tanpa internet |
| Keamanan | Login wajib, hak akses per peran, kunci rahasia tidak ada di dalam aplikasi |
| Biaya | Rp0 selama masih dalam batas paket gratis |
| Android | Android 8.0 (API 26) ke atas |
| Cadangan | Ekspor CSV berkala |

## 10. Metrik keberhasilan

| Metrik | Target 3 bulan |
|---|---|
| Waktu rata-rata dari ketik sampai jawaban | Kurang dari 10 detik |
| Pencarian yang menghasilkan jawaban berlabel pasti atau mungkin | Lebih dari 80% |
| Jumlah kelompok barang yang sudah berstatus pasti | Bertambah tiap minggu |
| Retur atau salah barang karena kecocokan | Turun dibandingkan sebelum memakai aplikasi (catat dari sekarang) |
| Kasir memakai aplikasi tiap hari | Semua kasir |

## 11. Risiko dan penanganan

| Risiko | Penanganan |
|---|---|
| Data internet salah | Label "mungkin", butuh konfirmasi pemilik, peringatan cek fisik |
| Data awal kosong sehingga aplikasi terasa tidak berguna | Isi dulu 30-50 barang paling sering dicari, impor Excel jika ada |
| Kasir malas mengonfirmasi | Tombol konfirmasi sangat mudah (satu ketukan), tampil otomatis setelah barang dipilih |
| Foto memenuhi penyimpanan gratis | Kompres foto otomatis sebelum diunggah |
| Melewati batas harian paket gratis Cloudflare membuat permintaan gagal | Pencarian dilakukan di HP, sinkronisasi hanya mengambil data yang berubah, pantau pemakaian |
| Pengguna tidak terbiasa teknologi | Desain sangat sederhana, uji langsung dengan mekanik dan ayah pemilik |

## 12. Tahapan rilis

| Tahap | Isi | Hasil |
|---|---|---|
| 0 | Mengisi data contoh (30-50 barang) dan menyepakati desain | Data awal + DESIGN.md disetujui |
| 1 | P0: cari, label, detail, bandingkan, konfirmasi, login | Versi uji di 1-2 HP |
| 2 | Uji di toko selama 2-4 minggu, perbaiki | Versi stabil untuk semua kasir |
| 3 | P1: cari internet semi-manual, impor/ekspor, riwayat | Data tumbuh lebih cepat |
| 4 | P2: otomatis, barcode, stok | Sesuai kebutuhan |

## 13. Pertanyaan yang masih terbuka

1. Apakah satu konfirmasi dari kasir cukup, atau perlu dua konfirmasi sebelum pemilik menyetujui?
2. Kategori barang apa yang diprioritaskan lebih dulu (lampu, kaca, kampas rem, filter, lainnya)?
3. Merek dan model mobil apa yang paling sering dicari di toko?
4. Apakah sudah ada data di Excel atau buku catatan yang bisa diimpor?
5. Berapa kasir dan mekanik yang akan memakai, dan di HP seperti apa?
6. Nama aplikasi yang diinginkan.
7. Kapan stok dan harga mulai dibutuhkan?
