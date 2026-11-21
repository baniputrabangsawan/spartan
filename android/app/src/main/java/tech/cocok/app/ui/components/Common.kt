package tech.cocok.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Tombol utama: lebar penuh, 56 dp, selalu ada tulisan (README §7.4).
 * Maksimal satu per layar; pemakaiannya dijaga di tiap layar.
 */
@Composable
fun TombolUtama(
    teks: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikon: ImageVector? = null,
    aktif: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = aktif,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CocokColors.BiruUtama,
            contentColor = CocokColors.Latar,
        ),
    ) {
        if (ikon != null) {
            Icon(ikon, contentDescription = null, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        }
        Text(teks, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tombol kedua: garis tepi, bukan biru penuh. */
@Composable
fun TombolGaris(
    teks: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikon: ImageVector? = null,
    warnaTeks: androidx.compose.ui.graphics.Color = CocokColors.BiruUtama,
    warnaGaris: androidx.compose.ui.graphics.Color = CocokColors.GarisTipis,
    aktif: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = aktif,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, warnaGaris),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = warnaTeks),
    ) {
        if (ikon != null) {
            Icon(ikon, contentDescription = null, modifier = Modifier.size(22.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
        }
        Text(teks, style = MaterialTheme.typography.labelLarge)
    }
}

/** Kartu putih, sudut membulat, bayangan sangat tipis (README §7). */
@Composable
fun Kartu(
    modifier: Modifier = Modifier,
    border: Boolean = false,
    onClick: (() -> Unit)? = null,
    isi: @Composable () -> Unit,
) {
    val bentuk = RoundedCornerShape(Dimensi.SudutKartu)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = bentuk,
            color = CocokColors.Latar,
            border = if (border) BorderStroke(1.dp, CocokColors.GarisTipis) else null,
            shadowElevation = if (border) 0.dp else 1.dp,
        ) { Box(Modifier.padding(Dimensi.JarakLayar * 0.7f)) { isi() } }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = bentuk,
            color = CocokColors.Latar,
            border = if (border) BorderStroke(1.dp, CocokColors.GarisTipis) else null,
            shadowElevation = if (border) 0.dp else 1.dp,
        ) { Box(Modifier.padding(Dimensi.JarakLayar * 0.7f)) { isi() } }
    }
}

/** Foto barang: selalu persegi, latar putih, tanpa foto asli -> inisial kategori. */
@Composable
fun FotoBarang(
    ukuran: androidx.compose.ui.unit.Dp,
    inisial: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(ukuran)
            .background(CocokColors.BelumLatar, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = inisial,
            style = MaterialTheme.typography.titleLarge,
            color = CocokColors.BelumTeks,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Chip pilihan yang bisa diketuk (mobil, tahun, kategori). */
@Composable
fun ChipPilihan(
    teks: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    terpilih: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (terpilih) CocokColors.BiruUtama else CocokColors.BelumLatar,
        border = null,
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                teks,
                style = MaterialTheme.typography.bodyLarge,
                color = if (terpilih) CocokColors.Latar else CocokColors.Teks,
            )
        }
    }
}

/** Angka bulat untuk langkah berurutan (Bandingkan: 1, 2, 3). */
@Composable
fun NomorLangkah(nomor: Int) {
    Box(
        modifier = Modifier.size(28.dp).background(CocokColors.BiruUtama, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nomor.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = CocokColors.Latar,
        )
    }
}

@Composable
fun BarisJudul(judul: String, modifier: Modifier = Modifier) {
    Text(
        judul,
        style = MaterialTheme.typography.titleSmall,
        color = CocokColors.TeksPendamping,
        modifier = modifier,
    )
}

@Composable
fun KolomBerkas(isi: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol)) { isi() }
}

@Composable
fun BarisRapat(isi: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil), verticalAlignment = Alignment.CenterVertically) { isi() }
}
