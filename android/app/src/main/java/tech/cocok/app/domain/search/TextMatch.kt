package tech.cocok.app.domain.search

/**
 * Normalisasi teks pencarian + kemiripan kata.
 *
 * Semua berjalan di HP (README §6.1) dan sengaja memakai aturan
 * sederhana yang bisa diuji: huruf kecil, buang tanda baca, lalu bandingkan
 * dengan jarak Levenshtein.
 */
object TextMatch {

    private val BUKAN_HURUF = Regex("[^a-z0-9 ]")

    /** "Stop Hilux Rebon 2018!" -> ["stop", "hilux", "rebon", "2018"] */
    fun kata(teks: String): List<String> =
        BUKAN_HURUF.replace(teks.lowercase(), " ")
            .split(' ')
            .filter { it.isNotBlank() }

    fun normal(teks: String): String = kata(teks).joinToString(" ")

    /**
     * Jarak edit antara dua kata. Dipakai untuk salah ketik seperti
     * "rebon"/"reborn" dan "avansa"/"avanza".
     */
    fun jarak(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var sebelumnya = IntArray(b.length + 1) { it }
        var sekarang = IntArray(b.length + 1)
        for (i in 1..a.length) {
            sekarang[0] = i
            for (j in 1..b.length) {
                val biaya = if (a[i - 1] == b[j - 1]) 0 else 1
                sekarang[j] = minOf(
                    sekarang[j - 1] + 1,
                    sebelumnya[j] + 1,
                    sebelumnya[j - 1] + biaya,
                )
            }
            val tukar = sebelumnya
            sebelumnya = sekarang
            sekarang = tukar
        }
        return sebelumnya[b.length]
    }

    /** Toleransi salah ketik menurut panjang kata: makin panjang, makin longgar. */
    fun toleransi(kata: String): Int = when {
        kata.length <= 3 -> 0
        kata.length <= 5 -> 1
        kata.length <= 8 -> 2
        else -> 3
    }

    /**
     * True bila [ketikan] cukup mirip dengan [target] (termasuk awalan kata).
     *
     * **Kata yang mengandung angka dicocokkan persis.** Satu digit berbeda
     * pada nomor part (81560 vs 81150) adalah barang yang berbeda; toleransi
     * salah ketik di sini akan menghasilkan jawaban "cocok" yang salah.
     */
    fun mirip(ketikan: String, target: String): Boolean {
        if (ketikan == target) return true
        if (ketikan.any(Char::isDigit) || target.any(Char::isDigit)) return false
        if (target.startsWith(ketikan) && ketikan.length >= 3) return true
        if (ketikan.startsWith(target) && target.length >= 3) return true
        return jarak(ketikan, target) <= toleransi(target)
    }

    /** True bila semua kata [ketikan] ditemukan di [teks] (dengan toleransi). */
    fun semuaCocok(ketikan: List<String>, teks: String): Boolean {
        val kataTeks = kata(teks)
        return ketikan.all { k -> kataTeks.any { t -> mirip(k, t) } }
    }
}
