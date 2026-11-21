package tech.cocok.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tech.cocok.app.domain.model.Status
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Label hasil: warna + ikon + kata + kalimat (README §7.1).
 * Selalu tiga-tiganya, jadi tetap terbaca oleh pengguna buta warna.
 */
data class TampilanStatus(
    val kata: String,
    val penjelas: String,
    val ikon: ImageVector,
    val teks: Color,
    val latar: Color,
)

fun Status.tampilan(): TampilanStatus = when (this) {
    Status.PASTI -> TampilanStatus(
        kata = "PASTI COCOK",
        penjelas = "Sudah dicek, aman dipasang",
        ikon = Icons.Filled.CheckCircle,
        teks = CocokColors.PastiTeks,
        latar = CocokColors.PastiLatar,
    )
    Status.MUNGKIN -> TampilanStatus(
        kata = "MUNGKIN COCOK",
        penjelas = "Belum dicek. Lihat barangnya dulu.",
        ikon = Icons.Filled.WarningAmber,
        teks = CocokColors.MungkinTeks,
        latar = CocokColors.MungkinLatar,
    )
    Status.TIDAK_COCOK -> TampilanStatus(
        kata = "TIDAK COCOK",
        penjelas = "Jangan dipasang",
        ikon = Icons.Filled.Cancel,
        teks = CocokColors.TidakCocokTeks,
        latar = CocokColors.TidakCocokLatar,
    )
    Status.BELUM_ADA_DATA -> TampilanStatus(
        kata = "BELUM ADA DATA",
        penjelas = "Belum ada info. Bisa dicari.",
        ikon = Icons.Filled.RemoveCircleOutline,
        teks = CocokColors.BelumTeks,
        latar = CocokColors.BelumLatar,
    )
}

/**
 * Label besar. Ukuran tidak pernah lebih kecil dari nama barang (README §7.1),
 * jadi pemanggil tidak boleh mengecilkannya.
 */
@Composable
fun StatusBadge(
    status: Status,
    modifier: Modifier = Modifier,
    sertakanPenjelas: Boolean = true,
) {
    val tampilan = status.tampilan()
    Row(
        modifier = modifier
            .background(tampilan.latar, RoundedCornerShape(12.dp))
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "${tampilan.kata}. ${tampilan.penjelas}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(tampilan.ikon, contentDescription = null, tint = tampilan.teks, modifier = Modifier.width(24.dp))
        Text(
            text = tampilan.kata,
            style = MaterialTheme.typography.titleMedium,
            color = tampilan.teks,
        )
    }
    if (sertakanPenjelas) {
        Text(
            text = tampilan.penjelas,
            style = MaterialTheme.typography.bodyMedium,
            color = CocokColors.TeksPendamping,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

/** Versi ringkas untuk kartu sempit: ikon + kata, tanpa kalimat pendamping. */
@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val tampilan = status.tampilan()
    Row(
        modifier = modifier
            .background(tampilan.latar, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(tampilan.ikon, contentDescription = null, tint = tampilan.teks, modifier = Modifier.width(18.dp))
        Text(tampilan.kata, style = MaterialTheme.typography.labelMedium, color = tampilan.teks)
    }
}

/** Pembungkus supaya isi baris bisa diatur pemanggil. */
@Composable
fun RowScope.Spacer(minimum: androidx.compose.ui.unit.Dp = Dimensi.JarakKecil) {
    androidx.compose.foundation.layout.Spacer(Modifier.width(minimum))
}

@Suppress("unused")
private val ikonTandaTanya = Icons.Filled.HelpOutline
