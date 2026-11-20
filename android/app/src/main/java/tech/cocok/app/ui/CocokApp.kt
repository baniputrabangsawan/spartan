package tech.cocok.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.search.nomorTampil
import tech.cocok.app.ui.screens.BandingkanScreen
import tech.cocok.app.ui.screens.CariInternetScreen
import tech.cocok.app.ui.screens.CariScreen
import tech.cocok.app.ui.screens.MatchScreen
import tech.cocok.app.ui.screens.DetailScreen
import tech.cocok.app.ui.screens.HasilScreen
import tech.cocok.app.ui.screens.LoginScreen
import tech.cocok.app.ui.screens.PengaturanScreen
import tech.cocok.app.ui.screens.PenggantiScreen
import tech.cocok.app.ui.screens.PitaOffline
import tech.cocok.app.ui.screens.TambahScreen
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/** Tujuan navigasi tingkat atas. Hanya tiga, sesuai DESIGN.md §7. */
private enum class Tab(val label: String, val ikon: ImageVector) {
    CARI("CARI", Icons.Filled.Search),
    BANDINGKAN("BANDINGKAN", Icons.Filled.SwapHoriz),
    TAMBAH("TAMBAH", Icons.Filled.Add),
}

/**
 * Kerangka aplikasi: header, isi, menu bawah tiga tombol.
 *
 * Layar detail/hasil/pengganti/riset menumpuk di atas tab dan menyimpan
 * tab asal, sehingga tombol kembali mengembalikan ke tempat yang benar.
 */
@Composable
fun CocokApp(vm: CocokViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.CARI) }
    var hasil by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<SearchHit?>(null) }
    var penggantiDari by remember { mutableStateOf<SearchHit?>(null) }
    var pengaturan by remember { mutableStateOf(false) }
    var riset by remember { mutableStateOf(false) }
    var foto by remember { mutableStateOf(false) }
    var kataGambar by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val layarFoto = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    if (!state.siap) {
        Column(
            Modifier.fillMaxSize().padding(Dimensi.JarakLayar),
            verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
        ) {
            Text("Cocok", style = MaterialTheme.typography.headlineMedium)
            if (state.memuat) {
                androidx.compose.material3.CircularProgressIndicator()
                Text("Membuka data di HP…", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(state.masalah ?: "Data belum bisa dibuka.", style = MaterialTheme.typography.bodyLarge)
                tech.cocok.app.ui.components.TombolUtama("Coba lagi", onClick = vm::muatData)
            }
        }
        return
    }

    if (!state.masuk) {
        Surface(color = CocokColors.Latar, modifier = Modifier.fillMaxSize()) {
            LoginScreen(vm)
        }
        return
    }

    fun bukaCari(teks: String) {
        vm.catatPencarian(teks)
        hasil = teks
        detail = null
        penggantiDari = null
    }

    // Tombol kembali sistem harus menutup lapisan paling atas, bukan keluar
    // aplikasi. Tanpa ini, di layar Pengaturan tombol back langsung menutup app.
    androidx.activity.compose.BackHandler(
        enabled = detail != null || penggantiDari != null || hasil != null || pengaturan || riset || foto,
    ) {
        when {
            riset -> riset = false
            pengaturan -> pengaturan = false
            penggantiDari != null -> penggantiDari = null
            detail != null -> detail = null
            foto -> foto = false
            hasil != null -> hasil = null
        }
    }

    if (state.pendingWrites > 0) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Text("Menyimpan di HP…", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        state.masalah?.let { message ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = vm::tutupMasalah,
                title = { Text("Belum tersimpan") },
                text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = vm::tutupMasalah) { Text("Periksa isian") }
                },
            )
        }
    }

    Surface(color = CocokColors.Latar, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header dengan tombol pengaturan di kanan atas (DESIGN.md §7).
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Dimensi.JarakLayar, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cocok",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CocokColors.Teks,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { pengaturan = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = CocokColors.TeksPendamping)
                }
            }

            if (!state.daring) PitaOffline()
            state.pesan?.let { PesanSingkat(it, vm::bersihkanPesan) }

            Box(Modifier.weight(1f)) {
                when {
                    foto && detail == null && penggantiDari == null -> layarFoto.SaveableStateProvider("foto") {
                        MatchScreen(
                            vm = vm,
                            onKembali = { foto = false },
                            initialKeyword = kataGambar,
                        )
                    }

                    riset -> CariInternetScreen(
                        vm = vm,
                        pertanyaan = vm.pertanyaanRiset(),
                        onSelesai = { riset = false },
                    )

                    pengaturan -> PengaturanScreen(
                        vm = vm,
                        onKembali = { pengaturan = false },
                        onKeluar = { pengaturan = false },
                    )

                    penggantiDari != null -> {
                        val asal = penggantiDari!!
                        PenggantiScreen(
                            vm = vm,
                            asal = asal,
                            onKembali = { penggantiDari = null },
                            onBukaDetail = { detail = it },
                        )
                    }

                    detail != null -> {
                        val hit = remember(detail, state.catalog) { vm.perbaruiHit(detail!!) }
                        DetailScreen(
                            vm = vm,
                            hit = hit,
                            onKembali = { detail = null },
                            onLihatPengganti = { penggantiDari = hit },
                        )
                    }

                    hasil != null -> {
                        val judul = hasil!!
                        val hits = remember(judul, state.catalog) { vm.cari(judul) }
                        HasilScreen(
                            vm = vm,
                            judul = judul,
                            hits = hits,
                            onKembali = { hasil = null },
                            onBukaDetail = { detail = it },
                            onTambahSendiri = { hasil = null; tab = Tab.TAMBAH },
                            onCariInternet = {
                                val kategori = vm.parse(judul).kategori.firstOrNull()
                                    ?: state.catalog.categories.first()
                                val kendaraan = vm.parse(judul).mobil.firstOrNull()
                                vm.mulaiRiset(kategori.id, kendaraan?.id)
                                riset = true
                            },
                        )
                    }

                    tab == Tab.CARI -> CariScreen(
                        vm = vm,
                        onCari = ::bukaCari,
                        onPilihKategori = { kat -> bukaCari(kat.nama) },
                        onPilihKendaraan = { v -> bukaCari(v.nama) },
                        onCariFoto = {
                            layarFoto.removeState("foto")
                            kataGambar = it
                            foto = true
                        },
                    )

                    tab == Tab.BANDINGKAN -> BandingkanScreen(vm)

                    else -> TambahScreen(vm)
                }
            }

            // Menu bawah hanya muncul di tab tingkat atas.
            val sembunyiMenu = detail != null || penggantiDari != null || pengaturan || riset || foto
            if (!sembunyiMenu) {
                MenuBawah(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun MenuBawah(aktif: Tab, onPilih: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = CocokColors.GarisTipis)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val terpilih = t == aktif
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        onClick = { onPilih(t) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (terpilih) CocokColors.PastiLatar else CocokColors.Latar,
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                t.ikon,
                                contentDescription = null,
                                tint = if (terpilih) CocokColors.BiruUtama else CocokColors.TeksPendamping,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                t.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (terpilih) CocokColors.BiruUtama else CocokColors.TeksPendamping,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pesan singkat hijau/kuning di atas isi layar (DESIGN.md §9). */
@Composable
private fun PesanSingkat(teks: String, onTutup: () -> Unit) {
    Surface(
        onClick = onTutup,
        shape = RoundedCornerShape(12.dp),
        color = CocokColors.PastiLatar,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensi.JarakLayar, vertical = 6.dp),
    ) {
        Text(
            teks,
            style = MaterialTheme.typography.bodyMedium,
            color = CocokColors.PastiTeks,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Suppress("unused")
private fun ringkas(hit: SearchHit) = hit.part.nomorTampil()
