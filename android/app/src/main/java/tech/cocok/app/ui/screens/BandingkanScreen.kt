package tech.cocok.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import tech.cocok.app.domain.model.CompareAnswer
import tech.cocok.app.domain.model.CompareOutcome
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Vehicle
import tech.cocok.app.domain.search.nomorTampil
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.NomorLangkah
import tech.cocok.app.ui.components.StatusChip
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.components.TombolUtama
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Layar BANDINGKAN (README §7.5).
 *
 * Dibuat seperti mengisi tiga kotak berurutan, bukan satu formulir panjang.
 * Jawabannya satu blok besar: SAMA / BEDA / BELUM TAHU.
 */
@Composable
fun BandingkanScreen(vm: CocokViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var kategori by remember { mutableStateOf<PartCategory?>(null) }
    var mobilA by remember { mutableStateOf<Vehicle?>(null) }
    var mobilB by remember { mutableStateOf<Vehicle?>(null) }
    var tahunA by remember { mutableStateOf<Int?>(null) }
    var tahunB by remember { mutableStateOf<Int?>(null) }
    var hasil by remember { mutableStateOf<CompareOutcome?>(null) }

    val siap = kategori != null && mobilA != null && mobilB != null && tahunA != null && tahunB != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Text("Bandingkan", style = MaterialTheme.typography.headlineMedium, color = CocokColors.Teks)
        Text(
            "Isi tiga kotak. Hasilnya SAMA, BEDA, atau BELUM TAHU.",
            style = MaterialTheme.typography.bodyMedium,
            color = CocokColors.TeksPendamping,
        )

        KotakPilihan(
            nomor = 1,
            label = "Barang apa?",
            nilai = kategori?.nama,
            pilihan = state.catalog.categories.map { it.nama },
            onPilih = { indeks ->
                kategori = state.catalog.categories[indeks]
                hasil = null
            },
        )

        KotakMobil(
            nomor = 2,
            label = "Mobil pertama",
            kendaraan = state.catalog.vehicles,
            dipilih = mobilA,
            tahun = tahunA,
            onPilihMobil = { mobilA = it; tahunA = it.yearTo; hasil = null },
            onPilihTahun = { tahunA = it; hasil = null },
        )

        KotakMobil(
            nomor = 3,
            label = "Mobil kedua",
            kendaraan = state.catalog.vehicles,
            dipilih = mobilB,
            tahun = tahunB,
            onPilihMobil = { mobilB = it; tahunB = it.yearTo; hasil = null },
            onPilihTahun = { tahunB = it; hasil = null },
        )

        TombolUtama(
            teks = "BANDINGKAN",
            aktif = siap,
            onClick = {
                val k = kategori
                val a = mobilA
                val b = mobilB
                val tA = tahunA
                val tB = tahunB
                if (k != null && a != null && b != null && tA != null && tB != null) {
                    hasil = vm.bandingkan(k, a, tA, b, tB)
                }
            },
        )

        hasil?.let { HasilBandingkan(it) }

        Spacer(Modifier.height(Dimensi.JarakLayar))
    }
}

@Composable
private fun KotakPilihan(
    nomor: Int,
    label: String,
    nilai: String?,
    pilihan: List<String>,
    onPilih: (Int) -> Unit,
) {
    var buka by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NomorLangkah(nomor)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
        }
        Box {
            Surface(
                onClick = { buka = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
                shape = RoundedCornerShape(12.dp),
                color = CocokColors.Latar,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        nilai ?: "Pilih",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (nilai == null) CocokColors.TeksPendamping else CocokColors.Teks,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = CocokColors.TeksPendamping)
                }
            }
            DropdownMenu(expanded = buka, onDismissRequest = { buka = false }) {
                pilihan.forEachIndexed { i, teks ->
                    DropdownMenuItem(
                        text = { Text(teks, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { buka = false; onPilih(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KotakMobil(
    nomor: Int,
    label: String,
    kendaraan: List<Vehicle>,
    dipilih: Vehicle?,
    tahun: Int?,
    onPilihMobil: (Vehicle) -> Unit,
    onPilihTahun: (Int) -> Unit,
) {
    var bukaMobil by remember { mutableStateOf(false) }
    var bukaTahun by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            NomorLangkah(nomor)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
            Box(Modifier.weight(1f)) {
                Surface(
                    onClick = { bukaMobil = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
                    shape = RoundedCornerShape(12.dp),
                    color = CocokColors.Latar,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dipilih?.nama ?: "Pilih mobil",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (dipilih == null) CocokColors.TeksPendamping else CocokColors.Teks,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = CocokColors.TeksPendamping)
                    }
                }
                DropdownMenu(expanded = bukaMobil, onDismissRequest = { bukaMobil = false }) {
                    kendaraan.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.namaDanTahun, style = MaterialTheme.typography.bodyLarge) },
                            onClick = { bukaMobil = false; onPilihMobil(v) },
                        )
                    }
                }
            }
            Box {
                Surface(
                    onClick = { if (dipilih != null) bukaTahun = true },
                    modifier = Modifier.heightIn(min = Dimensi.TombolTinggi),
                    shape = RoundedCornerShape(12.dp),
                    color = CocokColors.Latar,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tahun?.toString() ?: "Tahun",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (tahun == null) CocokColors.TeksPendamping else CocokColors.Teks,
                        )
                    }
                }
                val daftarTahun = dipilih?.let { v ->
                    val dari = v.yearFrom ?: 2000
                    val sampai = v.yearTo ?: 2026
                    (sampai downTo dari).toList()
                }.orEmpty()
                DropdownMenu(expanded = bukaTahun, onDismissRequest = { bukaTahun = false }) {
                    daftarTahun.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.toString(), style = MaterialTheme.typography.bodyLarge) },
                            onClick = { bukaTahun = false; onPilihTahun(t) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HasilBandingkan(outcome: CompareOutcome) {
    val warna = when (outcome.jawaban) {
        CompareAnswer.SAMA -> CocokColors.PastiTeks
        CompareAnswer.BEDA -> CocokColors.TidakCocokTeks
        CompareAnswer.BELUM_TAHU -> CocokColors.BelumTeks
    }
    val latar = when (outcome.jawaban) {
        CompareAnswer.SAMA -> CocokColors.PastiLatar
        CompareAnswer.BEDA -> CocokColors.TidakCocokLatar
        CompareAnswer.BELUM_TAHU -> CocokColors.BelumLatar
    }
    val judul = when (outcome.jawaban) {
        CompareAnswer.SAMA -> "SAMA"
        CompareAnswer.BEDA -> "BEDA"
        CompareAnswer.BELUM_TAHU -> "BELUM TAHU"
    }

    Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(latar, RoundedCornerShape(Dimensi.SudutKartu))
                .padding(Dimensi.JarakLayar),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(judul, style = MaterialTheme.typography.headlineMedium, color = warna)
            outcome.catatan?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = warna,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        BarisJudul(outcome.kategori.nama)

        SisiBanding(outcome, outcome.sisiA.kendaraan.nama, outcome.sisiA.tahun, outcome.sisiA.bagian.map { it.nomorTampil() }, outcome.sisiA.status)
        SisiBanding(outcome, outcome.sisiB.kendaraan.nama, outcome.sisiB.tahun, outcome.sisiB.bagian.map { it.nomorTampil() }, outcome.sisiB.status)

        if (outcome.partBersama.isNotEmpty()) {
            Kartu(border = true) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BarisJudul("Barang yang sama")
                    outcome.partBersama.forEach { p ->
                        Text(
                            "${p.name} · ${p.nomorTampil()}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CocokColors.Teks,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SisiBanding(
    outcome: CompareOutcome,
    nama: String,
    tahun: Int,
    nomor: List<String>,
    status: tech.cocok.app.domain.model.Status,
) {
    Kartu(border = true) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$nama $tahun", style = MaterialTheme.typography.titleLarge, color = CocokColors.Teks)
            StatusChip(status)
            if (nomor.isEmpty()) {
                Text("Belum ada nomor part tercatat.", style = MaterialTheme.typography.bodyMedium, color = CocokColors.TeksPendamping)
            } else {
                nomor.distinct().forEach { n ->
                    Text("→ $n", style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
                }
            }
        }
    }
}
