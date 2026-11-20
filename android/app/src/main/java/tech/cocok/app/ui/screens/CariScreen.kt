package tech.cocok.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Vehicle
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.ChipPilihan
import tech.cocok.app.ui.components.FotoBarang
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Layar CARI (DESIGN.md §8.1).
 *
 * Kotak cari besar di atas dan langsung siap diketik; di bawahnya chip yang
 * bisa diketuk supaya pengguna yang tidak yakin tetap bisa mulai tanpa mengetik.
 */
@Composable
fun CariScreen(
    vm: CocokViewModel,
    onCari: (String) -> Unit,
    onPilihKategori: (PartCategory) -> Unit,
    onPilihKendaraan: (Vehicle) -> Unit,
    onCariFoto: (String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var teks by remember { mutableStateOf("") }
    val fokus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun jalankan() {
        val nilai = teks.trim()
        if (nilai.isNotEmpty()) {
            keyboard?.hide()
            onCari(nilai)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Text(
            "Cari barang atau mobil",
            style = MaterialTheme.typography.headlineMedium,
            color = CocokColors.Teks,
        )

        OutlinedTextField(
            value = teks,
            onValueChange = { teks = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fokus),
            placeholder = {
                Text(
                    "Contoh: stop hilux rebon 2018",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CocokColors.TeksPendamping,
                )
            },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null) },
            textStyle = MaterialTheme.typography.titleLarge,
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CocokColors.BiruUtama,
                unfocusedBorderColor = CocokColors.GarisTipis,
                focusedTextColor = CocokColors.Teks,
                unfocusedTextColor = CocokColors.Teks,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { jalankan() }),
        )

        tech.cocok.app.ui.components.TombolUtama("CARI", onClick = ::jalankan, ikon = Icons.Filled.Search)

        // Fitur utama: satu barang -> kandidat barang sama/pengganti -> perbandingan bukti.
        tech.cocok.app.ui.components.TombolGaris(
            "Cari padanan barang",
            onClick = { keyboard?.hide(); onCariFoto(teks.trim()) },
            ikon = Icons.Filled.PhotoCamera,
        )

        BarisJudul("Yang sering dicari")
        val kategori = vm.kategoriSering()
        GridChip(isi = kategori.map { kat -> kat.nama to { onPilihKategori(kat) } })

        BarisJudul("Mobil yang sering dicari")
        GridChip(
            isi = vm.kendaraanSering().map { v -> (v.model + " " + (v.generation ?: "")).trim() to { onPilihKendaraan(v) } },
        )

        if (state.riwayat.isNotEmpty()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BarisJudul("Terakhir dicari")
                androidx.compose.material3.TextButton(onClick = vm::hapusRiwayat) {
                    Text("Hapus", style = MaterialTheme.typography.bodyMedium, color = CocokColors.BiruUtama)
                }
            }
            state.riwayat.forEach { r ->
                Kartu(onClick = { onCari(r.teks) }, border = true) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FotoBarang(40.dp, "↺")
                        Text(r.teks, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimensi.JarakLayar))
    }
}

/**
 * Dua kolom chip dengan lebar sama. Dipakai untuk kategori dan mobil supaya
 * tidak ada deretan panjang yang harus digeser.
 */
@Composable
private fun GridChip(isi: List<Pair<String, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
        isi.chunked(2).forEach { baris ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil),
                modifier = Modifier.fillMaxWidth(),
            ) {
                baris.forEach { (teks, aksi) ->
                    ChipPilihan(
                        teks = teks,
                        onClick = aksi,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (baris.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
