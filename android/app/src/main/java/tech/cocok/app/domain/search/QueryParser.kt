package tech.cocok.app.domain.search

import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Vehicle

/**
 * Hasil pengenalan satu ketikan: mobil, tahun, dan jenis barang yang terbaca.
 *
 * Semua properti boleh kosong. Yang kosong itulah yang ditanyakan ke pengguna
 * lewat pilihan yang bisa diketuk (DESIGN.md §8.1), bukan galat.
 */
data class ParsedQuery(
    val asli: String,
    val mobil: List<Vehicle> = emptyList(),
    val tahun: Int? = null,
    val kategori: List<PartCategory> = emptyList(),
    /** Kata yang tidak dikenali; dipakai untuk pencarian teks bebas. */
    val sisaKata: List<String> = emptyList(),
    val kataBarang: List<String> = emptyList(),
) {
    val adaMobil: Boolean get() = mobil.isNotEmpty()
    val adaKategori: Boolean get() = kategori.isNotEmpty()
    val kosong: Boolean get() = mobil.isEmpty() && kategori.isEmpty() && kataBarang.isEmpty()
}

/**
 * Parser sederhana namun deterministik — aturan ARCHITECTURE.md §7.1 langkah 1–4
 * tanpa jaringan.
 *
 * Dua keputusan yang menentukan hasilnya:
 * 1. **Generasi mobil** adalah penyaring bila disebut, dan hanya cadangan bila
 *    tidak disebut. "hilux 2018" harus tetap menemukan Hilux, bukan kosong.
 * 2. **Kata posisi** (kiri/kanan/depan/belakang) tidak wajib disebut. Orang toko
 *    mengetik "stop", bukan "lampu stop belakang".
 */
class QueryParser(
    private val kendaraan: List<Vehicle>,
    private val kategori: List<PartCategory>,
    private val sinonim: Map<String, String>,
) {

    private val kataPosisi = setOf("kiri", "kanan", "depan", "belakang", "atas", "bawah", "dalam", "luar")

    fun parse(teks: String): ParsedQuery {
        val kataAsli = TextMatch.kata(teks)
        if (kataAsli.isEmpty()) return ParsedQuery(teks)

        // 2. Angka 4 digit dianggap tahun.
        val tahun = kataAsli.firstOrNull { it.length == 4 && it.all(Char::isDigit) }?.toInt()

        // 3. Ganti sinonim (rebon -> reborn, avansa -> avanza).
        val kata = kataAsli.map { sinonim[it] ?: it }

        // 4a. Kenali mobil.
        val mobil = kenaliMobil(kata)

        // 4b. Kenali jenis barang menurut jumlah kata yang cocok.
        val kategoriCocok = kenaliKategori(kata)
        val terpakaiKategori = kategoriCocok.flatMap { indeksKategori(kata, it) }.toSet()

        val sisa = kata.filterIndexed { i, k ->
            i !in terpakaiKategori &&
                k != tahun?.toString() &&
                !bagianDariMobil(k) &&
                !k.all(Char::isDigit)
        }

        return ParsedQuery(
            asli = teks,
            mobil = mobil,
            tahun = tahun,
            kategori = kategoriCocok,
            sisaKata = sisa,
            kataBarang = sisa,
        )
    }

    /**
     * Dua tahap:
     *  - tahap 1: mobil yang generasinya disebut dan cocok ("reborn", "vigo");
     *  - tahap 2 (bila tahap 1 kosong): semua mobil yang modelnya disebut.
     * Tahap 2 sengaja mengembalikan beberapa kandidat; layar menampilkan pilihan.
     */
    private fun kenaliMobil(kata: List<String>): List<Vehicle> {
        val modelCocok = kendaraan.filter { modelDisebut(kata, it) }
        if (modelCocok.isEmpty()) return emptyList()

        val generasiCocok = modelCocok.filter { generasiDisebutDanCocok(kata, it) }
        return if (generasiCocok.isNotEmpty()) generasiCocok else modelCocok
    }

    private fun modelDisebut(kata: List<String>, v: Vehicle): Boolean {
        val model = TextMatch.kata(v.model)
        if (model.isEmpty()) return false
        return model.all { target -> kata.any { k -> TextMatch.mirip(k, target) } }
    }

    private fun generasiDisebutDanCocok(kata: List<String>, v: Vehicle): Boolean {
        val generasi = v.generation?.let { TextMatch.kata(it) }.orEmpty()
            .filterNot { it in kataPosisi || it == "generasi" }
        if (generasi.isEmpty()) return false
        val disebut = generasi.any { g -> kata.any { k -> TextMatch.mirip(k, g) } }
        return disebut && generasi.all { g -> kata.any { k -> TextMatch.mirip(k, g) } }
    }

    /**
     * Kata posisi diabaikan; sisanya dinilai. Jenis barang dengan kata khas
     * paling banyak yang menang, sehingga "lampu" saja tidak langsung memilih
     * antara lampu stop dan lampu depan.
     */
    private fun kenaliKategori(kata: List<String>): List<PartCategory> {
        val skor = kategori.map { kat -> kat to skorKategori(kata, kat) }
            .filter { it.second > 0 }
        if (skor.isEmpty()) return emptyList()
        val tertinggi = skor.maxOf { it.second }
        return skor.filter { it.second == tertinggi }.map { it.first }
    }

    private fun skorKategori(kata: List<String>, kat: PartCategory): Int =
        kataKhas(kat).count { target -> kata.any { k -> TextMatch.mirip(k, target) } }

    private fun indeksKategori(kata: List<String>, kat: PartCategory): List<Int> =
        kataKhas(kat).mapNotNull { target ->
            kata.indexOfFirst { k -> TextMatch.mirip(k, target) }.takeIf { it >= 0 }
        }

    private fun kataKhas(kat: PartCategory): List<String> =
        TextMatch.kata(kat.nama).filterNot { it in kataPosisi || it.all(Char::isDigit) }

    private fun bagianDariMobil(kata: String): Boolean =
        kendaraan.any { v ->
            TextMatch.kata("${v.brand} ${v.model} ${v.generation ?: ""}")
                .any { t -> TextMatch.mirip(kata, t) }
        }
}

/** Urutan tampilan kartu (DESIGN.md §8.2): pasti, mungkin, belum ada data. */
object HitOrdering {
    fun urutkan(hits: List<SearchHit>): List<SearchHit> = hits.sortedWith(
        compareByDescending<SearchHit> { it.status.peringkat }
            .thenBy { it.part.name.lowercase() },
    )

    val urutanLabel = listOf(Status.PASTI, Status.MUNGKIN, Status.BELUM_ADA_DATA)

    fun kelompokkan(hits: List<SearchHit>): List<Pair<Status, List<SearchHit>>> =
        urutanLabel.mapNotNull { status ->
            val isi = hits.filter { it.status == status }
            if (isi.isEmpty()) null else status to isi
        }

    /** Barang yang sudah dipastikan tidak cocok disembunyikan, tapi tetap bisa dibuka. */
    fun tersembunyi(hits: List<SearchHit>): List<SearchHit> =
        hits.filter { it.status == Status.TIDAK_COCOK }
}

fun Part.nomorTampil(): String = partNumber?.takeIf { it.isNotBlank() } ?: "Nomor part belum ada"
