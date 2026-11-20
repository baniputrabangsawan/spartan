package tech.cocok.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** DESIGN.md §6: tinggi tombol minimal 56 dp, jarak antar tombol 12 dp. */
object Dimensi {
    val TombolTinggi = 56.dp
    val KetukMinimum = 56.dp
    val JarakAntarTombol = 12.dp
    val JarakLayar = 20.dp
    val JarakKecil = 8.dp
    val SudutKartu = 16.dp
}

private val Skema = lightColorScheme(
    primary = CocokColors.BiruUtama,
    onPrimary = CocokColors.Latar,
    primaryContainer = CocokColors.PastiLatar,
    onPrimaryContainer = CocokColors.BiruUtama,
    background = CocokColors.Latar,
    onBackground = CocokColors.Teks,
    surface = CocokColors.Latar,
    onSurface = CocokColors.Teks,
    surfaceVariant = CocokColors.BelumLatar,
    onSurfaceVariant = CocokColors.TeksPendamping,
    outline = CocokColors.GarisTipis,
    error = CocokColors.TidakCocokTeks,
    onError = CocokColors.Latar,
)

/**
 * Tema aplikasi. Selalu terang (tidak ada mode gelap) supaya kontras label
 * status tetap sama di semua HP — keputusan dari DESIGN.md §4/§10.
 */
@Composable
fun CocokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Skema,
        typography = CocokTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(Dimensi.SudutKartu),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
