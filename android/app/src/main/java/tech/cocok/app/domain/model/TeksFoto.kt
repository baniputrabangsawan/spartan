package tech.cocok.app.domain.model

/**
 * Foto barang yang sedang dipakai untuk mencari (DESIGN.md §8.1b).
 *
 * [teks] adalah hasil pembacaan tulisan pada foto: nomor part, merek, atau
 * angka tahun. OCR tetap lokal; hanya kata pencarian yang dikirim ke Google,
 * bukan berkas fotonya.
 */
data class TeksFoto(
    val teks: String,
    val baris: List<String> = teks.split('\n').filter { it.isNotBlank() },
) {
    val kosong: Boolean get() = teks.isBlank()

    /** Nomor part yang terbaca: huruf + angka, minimal 4 karakter. */
    val nomorPart: List<String>
        get() = NOMOR_PART.findAll(teks.uppercase()).map { it.value }.distinct().toList()

    /** Tahun 19xx/20xx yang muncul di foto, untuk mengisi kotak tahun. */
    val tahun: Int?
        get() = TAHUN.findAll(teks)
            .mapNotNull { it.value.toIntOrNull() }
            .firstOrNull { it in 1950..2100 }

    private companion object {
        val NOMOR_PART = Regex("[A-Z0-9]{2,}[\\-./][A-Z0-9\\-./]{2,}")
        val TAHUN = Regex("\\b(19|20)\\d{2}\\b")
    }
}
