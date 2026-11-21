package tech.cocok.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warna persis dari README §7.2. Jangan menambah warna lain di layar:
 * satu warna utama, sisanya hanya untuk label status.
 */
object CocokColors {
    val BiruUtama = Color(0xFF1B5E9B)
    val Latar = Color(0xFFFFFFFF)
    val Teks = Color(0xFF1A1C1E)
    val TeksPendamping = Color(0xFF44474E)

    val PastiTeks = Color(0xFF1E7B34)
    val PastiLatar = Color(0xFFE6F4EA)

    val MungkinTeks = Color(0xFF7A4F00)
    val MungkinLatar = Color(0xFFFFF3CD)

    val TidakCocokTeks = Color(0xFFB3261E)
    val TidakCocokLatar = Color(0xFFFDE7E9)

    val BelumTeks = Color(0xFF5F6368)
    val BelumLatar = Color(0xFFEEF0F2)

    /** Pita offline (README §7.6) memakai nuansa kuning label "mungkin". */
    val PitaOfflineLatar = MungkinLatar
    val PitaOfflineTeks = MungkinTeks

    val GarisTipis = Color(0xFFE3E6EA)
}
