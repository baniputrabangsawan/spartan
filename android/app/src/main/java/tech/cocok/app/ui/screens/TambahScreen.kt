package tech.cocok.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Vehicle
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.components.TombolUtama
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

private enum class ModeTambah { PILIH, BARANG, MOBIL, HUBUNGKAN }

/**
 * Layar TAMBAH (README §7.5): tiga pilihan besar, lalu formulir pendek.
 * Semua data baru mulai dari "mungkin" — hanya pemilik yang bisa menaikkannya.
 */
@Composable
fun TambahScreen(vm: CocokViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ModeTambah.PILIH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Text("Tambah", style = MaterialTheme.typography.headlineMedium, color = CocokColors.Teks)

        when (mode) {
            ModeTambah.PILIH -> {
                Text(
                    "Pilih satu. Data baru selalu mulai sebagai MUNGKIN COCOK.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocokColors.TeksPendamping,
                )
                TombolUtama("Tambah barang", onClick = { mode = ModeTambah.BARANG }, ikon = Icons.Filled.Add)
                TombolGaris("Tambah mobil", onClick = { mode = ModeTambah.MOBIL }, ikon = Icons.Filled.Add)
                TombolGaris("Hubungkan barang dengan mobil", onClick = { mode = ModeTambah.HUBUNGKAN })
            }

            ModeTambah.BARANG -> FormBarang(
                kategori = state.catalog.categories,
                onBatal = { mode = ModeTambah.PILIH },
                onSimpan = { nama, kategoriId, nomor, merek ->
                    vm.tambahBarangDanKelompok(nama, kategoriId, nomor, merek) {
                        mode = ModeTambah.PILIH
                    }
                },
            )

            ModeTambah.MOBIL -> FormMobil(
                onBatal = { mode = ModeTambah.PILIH },
                onSimpan = { brand, model, generasi, dari, sampai ->
                    vm.tambahKendaraan(brand, model, generasi, dari, sampai) {
                        mode = ModeTambah.PILIH
                    }
                },
            )

            ModeTambah.HUBUNGKAN -> FormHubungkan(
                vm = vm,
                onBatal = { mode = ModeTambah.PILIH },
            )
        }

        Spacer(Modifier.height(Dimensi.JarakLayar))
    }
}

@Composable
private fun FormBarang(
    kategori: List<PartCategory>,
    onBatal: () -> Unit,
    onSimpan: (nama: String, kategoriId: String, nomor: String, merek: String) -> Unit,
) {
    var nama by remember { mutableStateOf("") }
    var nomor by remember { mutableStateOf("") }
    var merek by remember { mutableStateOf("") }
    var kategoriPilih by remember { mutableStateOf(kategori.firstOrNull()) }
    var buka by remember { mutableStateOf(false) }
    val valid = nama.isNotBlank() && kategoriPilih != null

    BarisJudul("Barang baru")
    KolomField("Nama barang (contoh: Lampu stop kiri)") {
        OutlinedTextField(
            value = nama,
            onValueChange = { nama = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }
    KolomField("Jenis barang") {
        androidx.compose.foundation.layout.Box {
            Surface(
                onClick = { buka = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
                shape = RoundedCornerShape(12.dp),
                color = CocokColors.Latar,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        kategoriPilih?.nama ?: "Pilih jenis",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (kategoriPilih == null) CocokColors.TeksPendamping else CocokColors.Teks,
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = buka, onDismissRequest = { buka = false }) {
                kategori.forEach { k ->
                    DropdownMenuItem(
                        text = { Text(k.nama, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { buka = false; kategoriPilih = k },
                    )
                }
            }
        }
    }
    KolomField("Nomor part (boleh dikosongkan)") {
        OutlinedTextField(
            value = nomor,
            onValueChange = { nomor = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }
    KolomField("Merek (kosongkan bila asli)") {
        OutlinedTextField(
            value = merek,
            onValueChange = { merek = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }

    TombolUtama(
        teks = "SIMPAN",
        aktif = valid,
        onClick = { kategoriPilih?.let { onSimpan(nama, it.id, nomor, merek) } },
    )
    TombolGaris("Batal", onClick = onBatal)
}

@Composable
private fun FormMobil(
    onBatal: () -> Unit,
    onSimpan: (brand: String, model: String, generasi: String, dari: Int?, sampai: Int?) -> Unit,
) {
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var generasi by remember { mutableStateOf("") }
    var dari by remember { mutableStateOf("") }
    var sampai by remember { mutableStateOf("") }
    val valid = brand.isNotBlank() && model.isNotBlank()

    BarisJudul("Mobil baru")
    KolomField("Merek (contoh: Toyota)") {
        OutlinedTextField(
            value = brand,
            onValueChange = { brand = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }
    KolomField("Model (contoh: Hilux)") {
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }
    KolomField("Generasi (contoh: Reborn, boleh kosong)") {
        OutlinedTextField(
            value = generasi,
            onValueChange = { generasi = it },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
        )
    }
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
        KolomField("Tahun awal", Modifier.weight(1f)) {
            OutlinedTextField(
                value = dari,
                onValueChange = { dari = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        KolomField("Tahun akhir", Modifier.weight(1f)) {
            OutlinedTextField(
                value = sampai,
                onValueChange = { sampai = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }

    TonbolSimpanBatal(
        valid = valid,
        onSimpan = {
            onSimpan(brand, model, generasi, dari.toIntOrNull(), sampai.toIntOrNull())
        },
        onBatal = onBatal,
    )
}

@Composable
private fun FormHubungkan(vm: CocokViewModel, onBatal: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var barang by remember { mutableStateOf<tech.cocok.app.domain.model.Part?>(null) }
    var mobil by remember { mutableStateOf<Vehicle?>(null) }
    var dari by remember { mutableStateOf("") }
    var sampai by remember { mutableStateOf("") }
    var bukaBarang by remember { mutableStateOf(false) }
    var bukaMobil by remember { mutableStateOf(false) }
    val valid = barang != null && mobil != null

    BarisJudul("Hubungkan barang dengan mobil")
    Text(
        "Dihubungkan sebagai MUNGKIN COCOK. Pemilik menetapkan PASTI setelah terbukti terpasang.",
        style = MaterialTheme.typography.bodyMedium,
        color = CocokColors.TeksPendamping,
    )

    KolomField("Barang") {
        androidx.compose.foundation.layout.Box {
            Surface(
                onClick = { bukaBarang = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
                shape = RoundedCornerShape(12.dp),
                color = CocokColors.Latar,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
            ) {
                androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        barang?.name ?: "Pilih barang",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (barang == null) CocokColors.TeksPendamping else CocokColors.Teks,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = bukaBarang, onDismissRequest = { bukaBarang = false }) {
                state.catalog.parts.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name + (p.partNumber?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { bukaBarang = false; barang = p },
                    )
                }
            }
        }
    }

    KolomField("Mobil") {
        androidx.compose.foundation.layout.Box {
            Surface(
                onClick = { bukaMobil = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimensi.TombolTinggi),
                shape = RoundedCornerShape(12.dp),
                color = CocokColors.Latar,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CocokColors.GarisTipis),
            ) {
                androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        mobil?.namaDanTahun ?: "Pilih mobil",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (mobil == null) CocokColors.TeksPendamping else CocokColors.Teks,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = bukaMobil, onDismissRequest = { bukaMobil = false }) {
                state.catalog.vehicles.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(v.namaDanTahun, style = MaterialTheme.typography.bodyLarge) },
                        onClick = { bukaMobil = false; mobil = v },
                    )
                }
            }
        }
    }

    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
        KolomField("Tahun awal", Modifier.weight(1f)) {
            OutlinedTextField(
                value = dari,
                onValueChange = { dari = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        KolomField("Tahun akhir", Modifier.weight(1f)) {
            OutlinedTextField(
                value = sampai,
                onValueChange = { sampai = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }

    TonbolSimpanBatal(
        valid = valid,
        onSimpan = {
            val p = barang
            val m = mobil
            if (p != null && m != null) {
                vm.hubungkan(p, m, dari.toIntOrNull(), sampai.toIntOrNull(), onSaved = onBatal)
            }
        },
        onBatal = onBatal,
    )
}

@Composable
private fun TonbolSimpanBatal(valid: Boolean, onSimpan: () -> Unit, onBatal: () -> Unit) {
    TombolUtama("SIMPAN", aktif = valid, onClick = onSimpan)
    TombolGaris("Batal", onClick = onBatal)
}

@Composable
private fun KolomField(label: String, modifier: Modifier = Modifier, isi: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CocokColors.TeksPendamping)
        isi()
    }
}

@Composable
private fun BarisJudul(judul: String) {
    Text(judul, style = MaterialTheme.typography.titleLarge, color = CocokColors.Teks)
}
