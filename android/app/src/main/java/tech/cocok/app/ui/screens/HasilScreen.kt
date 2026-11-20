package tech.cocok.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.search.HitOrdering
import tech.cocok.app.domain.search.nomorTampil
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.FotoBarang
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.StatusChip
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.components.TombolUtama
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Layar HASIL (DESIGN.md §8.2).
 *
 * Satu kartu per barang, foto di kiri, label di bawah nama. Urutan selalu
 * pasti -> mungkin -> belum ada data. Barang "tidak cocok" disembunyikan
 * tetapi bisa dibuka, karena informasi itu tetap berguna ("jangan dipasang").
 */
@Composable
fun HasilScreen(
    vm: CocokViewModel,
    judul: String,
    hits: List<SearchHit>,
    onKembali: () -> Unit,
    onBukaDetail: (SearchHit) -> Unit,
    onTambahSendiri: () -> Unit,
    onCariInternet: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val kelompok = remember(hits) { HitOrdering.kelompokkan(hits) }
    val tersembunyi = remember(hits) { HitOrdering.tersembunyi(hits) }
    var tampilkanTidakCocok by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        if (!state.daring) PitaOffline()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onKembali) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = CocokColors.Teks)
            }
            Text(
                judul,
                style = MaterialTheme.typography.headlineSmall,
                color = CocokColors.Teks,
                modifier = Modifier.weight(1f),
            )
        }

        if (hits.isEmpty()) {
            KolomKosongHasil(onTambahSendiri = onTambahSendiri, onCariInternet = onCariInternet)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimensi.JarakLayar),
            verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
            contentPadding = PaddingValues(bottom = Dimensi.JarakLayar),
        ) {
            kelompok.forEach { (status, isi) ->
                item(key = "judul-${status.name}") {
                    Text(
                        when (status) {
                            Status.PASTI -> "Sudah dipastikan"
                            Status.MUNGKIN -> "Perlu dicek dulu"
                            Status.BELUM_ADA_DATA -> "Belum ada data"
                            Status.TIDAK_COCOK -> "Tidak cocok"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = CocokColors.TeksPendamping,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(isi, key = { it.part.id }) { hit ->
                    KartuHasil(hit = hit, onClick = { onBukaDetail(hit) })
                }
            }

            if (tersembunyi.isNotEmpty()) {
                item(key = "tidak-cocok-toggle") {
                    TombolGaris(
                        teks = if (tampilkanTidakCocok) "Sembunyikan yang tidak cocok"
                        else "Lihat yang tidak cocok (${tersembunyi.size})",
                        onClick = { tampilkanTidakCocok = !tampilkanTidakCocok },
                        warnaTeks = CocokColors.TidakCocokTeks,
                        warnaGaris = CocokColors.TidakCocokTeks,
                    )
                }
                if (tampilkanTidakCocok) {
                    items(tersembunyi, key = { "x-${it.part.id}" }) { hit ->
                        KartuHasil(hit = hit, onClick = { onBukaDetail(hit) })
                    }
                }
            }

            item(key = "tambah") {
                TombolGaris(
                    teks = "Barangnya tidak ada di daftar? Tambah sendiri",
                    onClick = onTambahSendiri,
                    ikon = Icons.Filled.Add,
                )
            }
        }
    }
}

@Composable
private fun KartuHasil(hit: SearchHit, onClick: () -> Unit) {
    Kartu(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FotoBarang(84.dp, inisial(hit.part))
            Column(Modifier.weight(1f)) {
                Text(
                    hit.part.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = CocokColors.Teks,
                )
                Text(
                    hit.part.nomorTampil(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocokColors.TeksPendamping,
                )
                Spacer(Modifier.height(8.dp))
                StatusChip(hit.status)
                if (hit.status == Status.MUNGKIN) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cek barang langsung sebelum dijual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CocokColors.MungkinTeks,
                    )
                }
            }
        }
    }
}

@Composable
private fun KolomKosongHasil(onTambahSendiri: () -> Unit, onCariInternet: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimensi.JarakLayar),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Text("Belum ada data.", style = MaterialTheme.typography.titleLarge, color = CocokColors.Teks)
        Text(
            "Barang ini belum pernah dicatat untuk mobil tersebut. Bisa dicari di internet atau ditambahkan sendiri.",
            style = MaterialTheme.typography.bodyLarge,
            color = CocokColors.TeksPendamping,
        )
        TombolUtama("Cari di internet", onClick = onCariInternet)
        TombolGaris("Tambah sendiri", onClick = onTambahSendiri, ikon = Icons.Filled.Add)
    }
}

/** Pita kuning saat sinyal hilang (DESIGN.md §9). */
@Composable
fun PitaOffline() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensi.JarakLayar, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.WifiOff,
            contentDescription = null,
            tint = CocokColors.PitaOfflineTeks,
            modifier = Modifier.width(20.dp),
        )
        Text(
            "Tidak ada internet. Data di HP tetap bisa dipakai.",
            style = MaterialTheme.typography.bodyMedium,
            color = CocokColors.PitaOfflineTeks,
        )
    }
}

internal fun inisial(part: Part): String =
    part.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString(" ") { it.take(3).uppercase() }
