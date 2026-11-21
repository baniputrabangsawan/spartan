package tech.cocok.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.search.nomorTampil
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.FotoBarang
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.StatusBadge
import tech.cocok.app.ui.components.StatusChip
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Layar DETAIL BARANG (README §7.5).
 *
 * Jawaban dulu (label besar di atas), penjelasan belakangan. Tombol konfirmasi
 * selalu di bawah dan besar; tombol pemilik hanya muncul untuk pemilik.
 */
@Composable
fun DetailScreen(
    vm: CocokViewModel,
    hit: SearchHit,
    onKembali: () -> Unit,
    onLihatPengganti: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var konfirmasiTidakCocok by remember { mutableStateOf(false) }
    val fitmentUtama = hit.matches.firstOrNull()

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
                hit.part.name,
                style = MaterialTheme.typography.headlineSmall,
                color = CocokColors.Teks,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimensi.JarakLayar),
            verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimensi.JarakLayar),
        ) {
            item {
                Kartu(border = true) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FotoBarang(200.dp, inisial(hit.part), modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                Column {
                    StatusBadge(hit.status)
                }
            }

            item {
                BarisJudul("Nomor part")
                Spacer(Modifier.height(Dimensi.JarakKecil))
                NomorPartBaris("Asli", if (hit.part.isOriginal) hit.part.nomorTampil() else "—")
                NomorPartBaris("Merek lain", if (!hit.part.isOriginal) hit.part.nomorTampil() else "—")
                if (hit.part.brand != null) NomorPartBaris("Merek", hit.part.brand)
            }

            if (hit.kendaraan.isNotEmpty()) {
                item {
                    BarisJudul("Cocok untuk")
                    Spacer(Modifier.height(Dimensi.JarakKecil))
                    val perKendaraan = hit.matches.mapNotNull { f ->
                        hit.kendaraan.firstOrNull { it.id == f.vehicleId }?.let { v ->
                            val tahun = when {
                                f.yearFrom != null && f.yearTo != null -> " ${f.yearFrom}–${f.yearTo}"
                                f.yearFrom != null -> " ${f.yearFrom} ke atas"
                                f.yearTo != null -> " sampai ${f.yearTo}"
                                else -> ""
                            }
                            val status = if (f.status == Status.PASTI) "" else "  (${f.status.name.lowercase()})"
                            "• ${v.nama}$tahun$status"
                        }
                    }.distinct()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        perKendaraan.forEach { teks ->
                            Text(teks, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
                        }
                    }
                }
            }

            if (!hit.catatan.isNullOrBlank()) {
                item {
                    BarisJudul("Catatan")
                    Spacer(Modifier.height(4.dp))
                    Text(hit.catatan!!, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
                }
            }

            if (hit.status == Status.MUNGKIN) {
                item {
                    Kartu(border = true) {
                        Text(
                            "Data ini belum diperiksa. Cek barangnya langsung sebelum dijual.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CocokColors.MungkinTeks,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol)) {
                    TombolGaris(
                        teks = "Barang sama (pengganti)",
                        onClick = onLihatPengganti,
                        ikon = Icons.Filled.SwapHoriz,
                    )
                    TombolGaris(
                        teks = "Sudah dipakai, cocok",
                        onClick = { fitmentUtama?.let { vm.konfirmasi(it.id, true) } },
                        ikon = Icons.Filled.CheckCircle,
                        warnaTeks = CocokColors.PastiTeks,
                        warnaGaris = CocokColors.PastiTeks,
                        aktif = fitmentUtama != null,
                    )
                    TombolGaris(
                        teks = "Ternyata tidak cocok",
                        onClick = { konfirmasiTidakCocok = true },
                        ikon = Icons.Filled.Cancel,
                        warnaTeks = CocokColors.TidakCocokTeks,
                        warnaGaris = CocokColors.TidakCocokTeks,
                        aktif = fitmentUtama != null,
                    )

                    if (state.role == Role.PEMILIK && fitmentUtama != null) {
                        HorizontalDivider(color = CocokColors.GarisTipis)
                        BarisJudul("Khusus pemilik")
                        TombolGaris(
                            teks = "Tetapkan PASTI COCOK",
                            onClick = { vm.tetapkanStatus(fitmentUtama.id, Status.PASTI) },
                            warnaTeks = CocokColors.PastiTeks,
                            warnaGaris = CocokColors.PastiTeks,
                        )
                        TombolGaris(
                            teks = "Tetapkan TIDAK COCOK",
                            onClick = { vm.tetapkanStatus(fitmentUtama.id, Status.TIDAK_COCOK) },
                            warnaTeks = CocokColors.TidakCocokTeks,
                            warnaGaris = CocokColors.TidakCocokTeks,
                        )
                    }
                }
            }
        }
    }

    if (konfirmasiTidakCocok) {
        AlertDialog(
            onDismissRequest = { konfirmasiTidakCocok = false },
            title = { Text("Tandai tidak cocok?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Konfirmasi disimpan di HP agar bisa ditinjau pemilik di perangkat ini. Belum dikirim ke server.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    fitmentUtama?.let { vm.konfirmasi(it.id, false) }
                    konfirmasiTidakCocok = false
                }) { Text("Ya, tidak cocok", color = CocokColors.TidakCocokTeks) }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiTidakCocok = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun NomorPartBaris(label: String, nilai: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CocokColors.TeksPendamping, modifier = Modifier.weight(1f))
        Text(nilai, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks, modifier = Modifier.weight(1.3f))
    }
}

/** Layar pengganti: semua barang di kelompok yang sama (F4). */
@Composable
fun PenggantiScreen(
    vm: CocokViewModel,
    asal: SearchHit,
    onKembali: () -> Unit,
    onBukaDetail: (SearchHit) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pengganti = remember(asal, state.catalog) { vm.pengganti(asal.part) }

    Column(Modifier.fillMaxSize()) {
        if (!state.daring) PitaOffline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onKembali) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = CocokColors.Teks)
            }
            Column {
                Text("Barang yang sama", style = MaterialTheme.typography.headlineSmall, color = CocokColors.Teks)
                Text(
                    "Untuk ${asal.part.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocokColors.TeksPendamping,
                )
            }
        }

        if (pengganti.isEmpty()) {
            Text(
                "Belum ada barang lain di kelompok ini.",
                style = MaterialTheme.typography.bodyLarge,
                color = CocokColors.TeksPendamping,
                modifier = Modifier.padding(Dimensi.JarakLayar),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimensi.JarakLayar),
            verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Dimensi.JarakLayar),
        ) {
            items(pengganti, key = { it.part.id }) { hit ->
                Kartu(onClick = { onBukaDetail(hit) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        FotoBarang(64.dp, inisial(hit.part))
                        Column(Modifier.weight(1f)) {
                            Text(hit.part.name, style = MaterialTheme.typography.titleLarge, color = CocokColors.Teks)
                            Text(
                                hit.part.nomorTampil(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CocokColors.TeksPendamping,
                            )
                        }
                        StatusChip(hit.status)
                    }
                }
            }
        }
    }
}
