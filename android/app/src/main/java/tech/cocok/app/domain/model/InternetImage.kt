package tech.cocok.app.domain.model

/** Hasil penelusuran web, bukan bukti kecocokan kendaraan. */
data class InternetImage(
    val title: String,
    val thumbnailUrl: String,
    val imageUrl: String,
    val sourceUrl: String,
)
