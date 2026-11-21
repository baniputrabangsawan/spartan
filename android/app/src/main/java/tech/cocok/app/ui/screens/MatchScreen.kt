package tech.cocok.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Box
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.launch
import tech.cocok.app.domain.model.CandidateOrigin
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.ComparisonKind
import tech.cocok.app.domain.model.ItemComparison
import tech.cocok.app.domain.model.MatchCandidate
import tech.cocok.app.domain.model.MatchContext
import tech.cocok.app.domain.model.MatchEvidence
import tech.cocok.app.domain.model.MatchItem
import tech.cocok.app.domain.model.MatchKind
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.SpecificationState
import tech.cocok.app.domain.usecase.MatchParts
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.MatchState
import tech.cocok.app.ui.MatchViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.ChipPilihan
import tech.cocok.app.ui.components.FotoProduk
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.StatusChip
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.components.TombolUtama
import tech.cocok.app.ui.foto.PembacaFoto
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

private enum class Langkah { SUMBER, KANDIDAT, BANDING }

/**
 * Pencocokan barang (alur utama): satu sumber -> kandidat -> perbandingan bukti.
 *
 * Foto polos memakai embedding lokal yang nyata; kemiripan bentuk, nomor part, dan
 * grup hanyalah petunjuk. "Pengganti terverifikasi" hanya muncul dari catatan pemilik
 * untuk mobil, tahun, dan varian yang sama.
 */
@Composable
fun MatchScreen(
    vm: CocokViewModel,
    onKembali: () -> Unit,
    initialKeyword: String = "",
) {
    val store by vm.state.collectAsStateWithLifecycle()
    val model: MatchViewModel = viewModel()
    val match by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var langkah by rememberSaveable { mutableStateOf(Langkah.SUMBER) }
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var uploadAsk by remember { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    var vehicleId by rememberSaveable { mutableStateOf("") }
    var tahun by rememberSaveable { mutableStateOf("") }
    var varianKunci by rememberSaveable { mutableStateOf("") }
    var varianNilai by rememberSaveable { mutableStateOf("") }
    var varian by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }
    var catatan by rememberSaveable { mutableStateOf("") }

    if (match.keyword.isBlank() && initialKeyword.isNotBlank()) model.editKeyword(initialKeyword)

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null && file.isFile) {
            val ref = PembacaFoto.uriUntuk(context, file).toString()
            model.setPhoto(ref)
            model.searchVisualLocal(store.catalog, Uri.parse(ref))
        }
    }
    val galeri = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val salinan = PembacaFoto.salin(context, uri)
                model.setPhoto(salinan.toString())
                model.searchVisualLocal(store.catalog, salinan)
            } catch (e: Exception) {
                error = e.message ?: "Foto tidak dapat dibaca."
            }
        }
    }

    val source = match.source
    // Compare by identity: edited/internet items carry no catalogPartId, so filtering
    // on that field would hide exactly the candidates being compared.
    val candidates = match.candidates.filter { it.item.id != source?.id }
    // Look the pick up in the full list: the source is excluded from the visible
    // candidates, and a candidate must never be lost because of that exclusion.
    val chosen = chosenId?.let { id -> match.candidates.firstOrNull { it.item.id == id } }
    val konteks = MatchContext(
        vehicleId = vehicleId.ifBlank { null },
        year = tahun.toIntOrNull(),
        variant = varian,
    )
    val perbandingan: ItemComparison? = if (source != null && chosen != null) {
        remember(source, chosen.item, konteks, store.catalog) {
            MatchParts().compare(store.catalog, source, chosen.item, konteks)
        }
    } else null

    fun buka(url: String) {
        if (!TautanPadanan.web(url)) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            error = "Tidak ada aplikasi untuk membuka tautan itu."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                when (langkah) {
                    Langkah.BANDING -> langkah = Langkah.KANDIDAT
                    Langkah.KANDIDAT -> langkah = Langkah.SUMBER
                    Langkah.SUMBER -> onKembali()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = CocokColors.Teks)
            }
            Text(
                when (langkah) {
                    Langkah.SUMBER -> "Cari padanan barang"
                    Langkah.KANDIDAT -> "Kandidat padanan"
                    Langkah.BANDING -> "Bandingkan barang"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = CocokColors.Teks,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
            verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
        ) {
            listOfNotNull(error, match.error).forEach {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = CocokColors.TidakCocokTeks)
            }

            when (langkah) {
                Langkah.SUMBER -> {
                    Text(
                        "Masukkan satu barang lewat foto atau kata kunci. Aplikasi mencari barang yang sama atau penggantinya.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CocokColors.TeksPendamping,
                    )
                    OutlinedTextField(
                        value = match.keyword,
                        onValueChange = model::editKeyword,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama barang, nomor part, atau mobil") },
                        textStyle = MaterialTheme.typography.titleMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    TombolGaris("Ambil foto barang", onClick = {
                        cameraFile = PembacaFoto.berkasBaru(context)
                        cameraFile?.let { kamera.launch(PembacaFoto.uriUntuk(context, it)) }
                    }, ikon = Icons.Filled.PhotoCamera)
                    TombolGaris("Pilih dari galeri", onClick = { galeri.launch("image/*") }, ikon = Icons.Filled.PhotoCamera)
                    if (match.photo != null) {
                        Text(
                            "Foto disalin ke penyimpanan aplikasi dan hanya dikirim bila Anda menyetujuinya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CocokColors.TeksPendamping,
                        )
                    }
                    TombolUtama("Cari kandidat", onClick = {
                        keyboard?.hide()
                        // Typed text (or the OCR text) is the source name until a store
                        // item is chosen, so the comparison screen is never empty.
                        model.searchLocal(store.catalog, label = match.keyword)
                        langkah = Langkah.KANDIDAT
                    }, ikon = Icons.Filled.Search)

                    BarisJudul("Atau mulai dari data toko")
                    if (store.catalog.parts.isEmpty()) {
                        Text(
                            "Belum ada barang di data toko. Tambah barang dulu, lalu ulangi pencarian padanan.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CocokColors.TeksPendamping,
                        )
                    }
                    store.catalog.parts.take(6).forEach { part ->
                        Kartu(onClick = {
                            model.useCatalogItem(part.id, store.catalog)
                            langkah = Langkah.KANDIDAT
                        }, border = true) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    part.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CocokColors.Teks,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusChip(grupStatus(store.catalog, part.id))
                            }
                        }
                    }
                }

                Langkah.KANDIDAT -> {
                    if (match.localLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text("Mencari kandidat di data toko...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (match.noPhotoIndex) {
                        Kartu(border = true) {
                            Text(
                                "Belum ada foto barang di data toko, jadi kemiripan bentuk belum bisa dihitung. " +
                                    "Tambah foto barang, pakai nomor part, atau cari kemiripan di internet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CocokColors.Teks,
                            )
                        }
                    }

                    BarisJudul("Kandidat dari data toko")
                    if (candidates.isEmpty() && !match.localLoading) {
                        Text(
                            "Tidak ada kandidat di data toko. Periksa nomor part, atau cari di internet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CocokColors.TeksPendamping,
                        )
                    }
                    candidates.forEach { candidate ->
                        KartuKandidat(candidate) {
                            chosenId = candidate.item.id
                            langkah = Langkah.BANDING
                        }
                    }

                    TombolGaris("Cari di Shopee", onClick = {
                        buka(TautanPadanan.shopee(match.keyword.ifBlank { source?.name.orEmpty() }))
                    }, ikon = Icons.Filled.Search)
                    match.photo?.let { path ->
                        TombolGaris("Cari kemiripan foto di internet", onClick = { uploadAsk = path })
                        Text(
                            "Mengirim foto ke layanan gambar publik setelah persetujuan; hasilnya kandidat, bukan bukti cocok.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CocokColors.TeksPendamping,
                        )
                    }
                    // Without a photo index or OCR text, keyword images are a real fallback.
                    TombolGaris(
                        "Cari gambar berdasarkan kata kunci",
                        onClick = { model.searchInternetKeyword(match.keyword.ifBlank { source?.name.orEmpty() }) },
                        aktif = !match.internetLoading,
                    )

                    if (match.internetLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text("Mencari kemiripan gambar...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (match.internet.isNotEmpty()) {
                        BarisJudul("Hasil gambar internet (kandidat)")
                        match.internet.forEach { image ->
                            Kartu(border = true) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FotoProduk(
                                        ukuran = 96.dp,
                                        alamat = image.thumbnailUrl,
                                        inisial = inisialDari(image.title),
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            image.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = CocokColors.Teks,
                                            maxLines = 3,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            Uri.parse(image.sourceUrl).host.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CocokColors.TeksPendamping,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = {
                                                chosenId = model.useInternetCandidate(image)
                                                langkah = Langkah.BANDING
                                            }) { Text("Bandingkan") }
                                            TextButton(onClick = { buka(image.sourceUrl) }) {
                                                Text("Buka sumber")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (match.internetDone && match.internet.isEmpty() && !match.internetLoading) {
                        Text(
                            "Layanan gambar tidak mengembalikan hasil. Foto tetap tersimpan di HP dan tidak hilang.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CocokColors.TeksPendamping,
                        )
                    }
                }

                Langkah.BANDING -> {
                    if (source == null || chosen == null || perbandingan == null) {
                        Text("Kandidat tidak lagi tersedia. Pilih ulang.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IdentitasKartu("Sumber", source, Modifier.weight(1f))
                            IdentitasKartu("Kandidat", chosen.item, Modifier.weight(1f))
                        }
                        Kartu(border = true) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    perbandingan.label,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = warna(perbandingan.kind),
                                )
                                Text(
                                    perbandingan.explanation,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CocokColors.Teks,
                                )
                            }
                        }

                        BarisJudul("Spesifikasi (ketuk untuk mengisi)")
                        perbandingan.rows.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Box(Modifier.weight(1f)) {
                                    SpecField(
                                        value = row.first,
                                        placeholder = "belum ada",
                                        onEdit = { nilai ->
                                            model.editItem(source.id) { item ->
                                                item.copy(specifications = item.specifications + (row.key to nilai))
                                            }
                                        },
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    SpecField(
                                        value = row.second,
                                        placeholder = "belum ada",
                                        onEdit = { nilai ->
                                            model.editItem(chosen.item.id) { item ->
                                                item.copy(specifications = item.specifications + (row.key to nilai))
                                            }
                                        },
                                    )
                                }
                                Text(
                                    when (row.state) {
                                        SpecificationState.SAME -> "sama"
                                        SpecificationState.DIFFERENT -> "beda"
                                        SpecificationState.MISSING -> "?"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when (row.state) {
                                        SpecificationState.SAME -> CocokColors.PastiTeks
                                        SpecificationState.DIFFERENT -> CocokColors.TidakCocokTeks
                                        SpecificationState.MISSING -> CocokColors.MungkinTeks
                                    },
                                )
                            }
                        }
                        Text(
                            "Isi hanya angka atau teks yang benar-benar terukur. Nilai kosong tetap dihitung belum ada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CocokColors.TeksPendamping,
                        )

                        if (perbandingan.checks.isNotEmpty()) {
                            BarisJudul("Yang masih perlu diperiksa")
                            perbandingan.checks.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
                            }
                        }

                        BarisJudul("Konteks mobil untuk verifikasi")
                        store.catalog.vehicles.forEach { kendaraan ->
                            ChipPilihan(
                                kendaraan.namaDanTahun,
                                onClick = { vehicleId = kendaraan.id },
                                terpilih = vehicleId == kendaraan.id,
                            )
                        }
                        OutlinedTextField(
                            value = tahun,
                            onValueChange = { tahun = it.filter(Char::isDigit).take(4) },
                            label = { Text("Tahun") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = varianKunci,
                                onValueChange = { varianKunci = it },
                                label = { Text("Varian") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = varianNilai,
                                onValueChange = { varianNilai = it },
                                label = { Text("Nilai") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TombolGaris("Tambah varian", onClick = {
                            val kunci = varianKunci.trim()
                            val nilai = varianNilai.trim()
                            if (kunci.isNotEmpty() && nilai.isNotEmpty()) {
                                varian = varian + (kunci to nilai)
                                varianKunci = ""
                                varianNilai = ""
                            }
                        })
                        varian.forEach { (kunci, nilai) ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("$kunci: $nilai", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { varian = varian - kunci }) { Text("Hapus") }
                            }
                        }

                        OutlinedTextField(
                            value = catatan,
                            onValueChange = { catatan = it },
                            label = { Text("Catatan pemeriksaan") },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                        )

                        if (store.role == Role.PEMILIK) {
                            val siap = perbandingan.canVerify && catatan.isNotBlank()
                            TombolGaris("Catat pengganti langsung", onClick = {
                                catatBukti(
                                    vm = vm,
                                    source = source,
                                    candidate = chosen.item,
                                    vehicleId = vehicleId,
                                    tahun = tahun,
                                    varian = varian,
                                    catatan = catatan,
                                    kind = MatchKind.DIRECT_REPLACEMENT,
                                    onError = { error = it },
                                    onSaved = { langkah = Langkah.KANDIDAT },
                                )
                            }, aktif = siap)
                            TombolGaris("Catat barang sama", onClick = {
                                catatBukti(
                                    vm = vm,
                                    source = source,
                                    candidate = chosen.item,
                                    vehicleId = vehicleId,
                                    tahun = tahun,
                                    varian = varian,
                                    catatan = catatan,
                                    kind = MatchKind.SAME_ITEM,
                                    onError = { error = it },
                                    onSaved = { langkah = Langkah.KANDIDAT },
                                )
                            }, aktif = siap)
                            TombolGaris("Catat bukan pengganti", onClick = {
                                catatBukti(
                                    vm = vm,
                                    source = source,
                                    candidate = chosen.item,
                                    vehicleId = vehicleId,
                                    tahun = tahun,
                                    varian = varian,
                                    catatan = catatan,
                                    kind = MatchKind.NOT_REPLACEMENT,
                                    onError = { error = it },
                                    onSaved = { langkah = Langkah.KANDIDAT },
                                )
                            }, aktif = siap, warnaTeks = CocokColors.TidakCocokTeks, warnaGaris = CocokColors.TidakCocokTeks)
                            Text(
                                "Verifikasi hanya berlaku untuk mobil, tahun, dan varian di atas. Isi catatan hasil pemeriksaan fisik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CocokColors.TeksPendamping,
                            )
                        } else {
                            Text(
                                "Hanya pemilik yang dapat mencatat verifikasi padanan. Kasir tetap bisa membandingkan dan menambah data.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CocokColors.TeksPendamping,
                            )
                        }

                        listOf(source, chosen.item).forEach { item ->
                            if (item.catalogPartId == null && item.linkedCatalogPartId == null) {
                                TombolGaris(
                                    teks = "Simpan \"${item.name.take(28)}\" ke data toko",
                                    onClick = { vm.simpanItemSebagaiBarang(item) },
                                )
                            }
                            val target = item.linkedCatalogPartId
                            if (target != null && item.specifications.isNotEmpty()) {
                                // Was keyed off catalogPartId, which editing clears - so the
                                // action never appeared and filled-in specs were discarded.
                                TombolGaris(
                                    teks = "Simpan spesifikasi \"${item.name.take(22)}\"",
                                    onClick = { vm.simpanSpesifikasi(target, item.specifications) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uploadAsk?.let { path ->
        AlertDialog(
            onDismissRequest = { uploadAsk = null },
            title = { Text("Kirim foto ini ke Google?") },
            text = {
                Text(
                    "Hanya foto yang Anda pilih yang dikirim untuk pencarian kemiripan gambar. " +
                        "Hasilnya bukan bukti kecocokan mobil.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    uploadAsk = null
                    model.searchInternet(Uri.parse(path))
                }) { Text("Kirim foto") }
            },
            dismissButton = { TextButton(onClick = { uploadAsk = null }) { Text("Batal") } },
        )
    }
}

private fun grupStatus(catalog: Catalog, partId: String) =
    catalog.groupParts.firstOrNull { it.partId == partId }?.let { catalog.statusPart(it.groupId, partId) }
        ?: tech.cocok.app.domain.model.Status.BELUM_ADA_DATA

private fun catatBukti(
    vm: CocokViewModel,
    source: MatchItem,
    candidate: MatchItem,
    vehicleId: String,
    tahun: String,
    varian: Map<String, String>,
    catatan: String,
    kind: MatchKind,
    onError: (String) -> Unit,
    onSaved: () -> Unit,
) {
    val pertama = source.catalogPartId
    val kedua = candidate.catalogPartId
    if (pertama == null || kedua == null) {
        onError("Simpan kedua barang ke data toko sebelum mencatat verifikasi pemilik.")
        return
    }
    val state = vm.state.value
    vm.simpanBuktiPadanan(
        MatchEvidence(
            id = UUID.randomUUID().toString(),
            firstPartId = pertama,
            secondPartId = kedua,
            vehicleId = vehicleId,
            year = tahun.toIntOrNull() ?: 0,
            variant = varian,
            kind = kind,
            note = catatan.trim(),
            by = state.namaPengguna,
            role = state.role,
            createdAt = Instant.now().toString(),
        ),
        onSaved = onSaved,
    )
}

@Composable
private fun KartuKandidat(candidate: MatchCandidate, onClick: () -> Unit) {
    Kartu(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    candidate.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CocokColors.Teks,
                    modifier = Modifier.weight(1f),
                )
                candidate.similarity?.let {
                    Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = CocokColors.BiruUtama)
                }
            }
            candidate.item.partNumber?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(candidate.reason, style = MaterialTheme.typography.bodySmall, color = CocokColors.TeksPendamping)
            Text("Bandingkan", style = MaterialTheme.typography.labelLarge, color = CocokColors.BiruUtama)
        }
    }
}

@Composable
private fun IdentitasKartu(judul: String, item: MatchItem, modifier: Modifier = Modifier) {
    Kartu(modifier = modifier, border = true) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(judul, style = MaterialTheme.typography.labelLarge, color = CocokColors.TeksPendamping)
            FotoProduk(ukuran = 88.dp, alamat = item.photo, inisial = inisialDari(item.name))
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = CocokColors.Teks, maxLines = 3)
            Text(item.partNumber ?: "Nomor part belum ada", style = MaterialTheme.typography.bodySmall)
            item.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            item.sourceUrl?.let { Text(Uri.parse(it).host.orEmpty(), style = MaterialTheme.typography.bodySmall) }
            Text(
                when (item.origin) {
                    CandidateOrigin.CATALOG -> "Dari data toko"
                    CandidateOrigin.INTERNET -> "Kandidat internet"
                    CandidateOrigin.INPUT -> if (item.photo != null) "Dari foto" else "Masukan manual"
                },
                style = MaterialTheme.typography.bodySmall,
                color = CocokColors.TeksPendamping,
            )
        }
    }
}

/** Inisial 2-3 huruf sebagai pengganti foto yang belum atau tidak termuat. */
private fun inisialDari(nama: String): String =
    nama.split(' ').filter { it.isNotBlank() }.take(2).joinToString(" ") { it.take(3).uppercase() }

@Composable
private fun warna(kind: ComparisonKind) = when (kind) {
    ComparisonKind.SAME_ITEM, ComparisonKind.VERIFIED_REPLACEMENT -> CocokColors.PastiTeks
    ComparisonKind.REJECTED -> CocokColors.TidakCocokTeks
    ComparisonKind.DIFFERENCES -> CocokColors.MungkinTeks
    ComparisonKind.CANDIDATE -> CocokColors.Teks
}

internal object TautanPadanan {
    fun shopee(keyword: String): String =
        "https://shopee.co.id/search?keyword=" + java.net.URLEncoder.encode(keyword, "UTF-8")

    fun web(url: String): Boolean = try {
        val uri = Uri.parse(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in setOf(-1, 443)
    } catch (_: Exception) {
        false
    }
}

/** Compact editable spec value; empty input honestly clears the value. */
@Composable
private fun SpecField(value: String?, placeholder: String, onEdit: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value.orEmpty()) }
    if (editing) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = {
                    onEdit(draft.trim())
                    editing = false
                }) { Text("OK") }
            },
        )
    } else {
        TextButton(onClick = {
            draft = value.orEmpty()
            editing = true
        }) {
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) CocokColors.TeksPendamping else CocokColors.BiruUtama,
            )
        }
    }
}
