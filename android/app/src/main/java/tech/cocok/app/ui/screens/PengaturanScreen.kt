package tech.cocok.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.cocok.app.domain.model.Role
import tech.cocok.app.ui.CocokViewModel
import tech.cocok.app.ui.components.BarisJudul
import tech.cocok.app.ui.components.ChipPilihan
import tech.cocok.app.ui.components.Kartu
import tech.cocok.app.ui.components.TombolGaris
import tech.cocok.app.ui.components.TombolUtama
import tech.cocok.app.ui.theme.CocokColors
import tech.cocok.app.ui.theme.Dimensi

/**
 * Layar masuk. Tidak ada pendaftaran terbuka (ARCHITECTURE.md §6):
 * akun dibuat pemilik. Di versi tampilan ini peran dipilih langsung.
 */
@Composable
fun LoginScreen(vm: CocokViewModel) {
    var nama by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.KASIR) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Cocok", style = MaterialTheme.typography.headlineMedium, color = CocokColors.BiruUtama)
        Text(
            "Cek kecocokan suku cadang dalam hitungan detik.",
            style = MaterialTheme.typography.bodyLarge,
            color = CocokColors.TeksPendamping,
        )

        Spacer(Modifier.height(16.dp))

        Text("Peran simulasi (bukan login server)",
            style = MaterialTheme.typography.bodyMedium, color = CocokColors.TeksPendamping)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
            ChipPilihan("Kasir", onClick = { role = Role.KASIR }, terpilih = role == Role.KASIR)
            ChipPilihan("Pemilik", onClick = { role = Role.PEMILIK }, terpilih = role == Role.PEMILIK)
        }

        OutlinedTextField(
            value = nama,
            onValueChange = { nama = it },
            label = { Text("Nama atau username", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )


        TombolUtama("BUKA DATA LOKAL", onClick = { vm.masuk(nama, role) })

        Kartu(border = true) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CocokColors.PastiTeks)
                    Text("Mode demo lokal", style = MaterialTheme.typography.titleSmall, color = CocokColors.Teks)
                }
                Text(
                    "Data contoh tersimpan di HP dengan Room. Nomor part belum diverifikasi. Login dan sinkronisasi Cloudflare belum tersedia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CocokColors.TeksPendamping,
                )
            }
        }
    }
}

/** Pengaturan: peran, status sambungan, dan keluar. */
@Composable
fun PengaturanScreen(vm: CocokViewModel, onKembali: () -> Unit, onKeluar: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onKembali) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = CocokColors.Teks,
                )
            }
            Text("Pengaturan", style = MaterialTheme.typography.headlineMedium, color = CocokColors.Teks)
        }

        Kartu(border = true) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BarisJudul("Masuk sebagai")
                Text(
                    "${state.namaPengguna} · ${state.role.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CocokColors.Teks,
                )
            }
        }

        BarisJudul("Tampilan peran (untuk uji)")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
            ChipPilihan("Kasir", onClick = { vm.gantiPeran(Role.KASIR) }, terpilih = state.role == Role.KASIR)
            ChipPilihan("Pemilik", onClick = { vm.gantiPeran(Role.PEMILIK) }, terpilih = state.role == Role.PEMILIK)
        }
        Text(
            "Kasir tidak bisa menetapkan PASTI COCOK. Hanya pemilik yang bisa.",
            style = MaterialTheme.typography.bodyMedium,
            color = CocokColors.TeksPendamping,
        )

        HorizontalDivider(color = CocokColors.GarisTipis)

        BarisJudul("Penyimpanan lokal")
        Text(
            "Data disimpan dengan Room di HP ini. Belum terhubung atau tersinkron ke Cloudflare.",
            style = MaterialTheme.typography.bodyLarge,
            color = CocokColors.Teks,
        )
        BarisJudul("Simulasi tampilan sambungan (untuk uji)")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensi.JarakKecil)) {
            ChipPilihan("Daring", onClick = { vm.aturDaring(true) }, terpilih = state.daring)
            ChipPilihan("Luring", onClick = { vm.aturDaring(false) }, terpilih = !state.daring)
        }

        HorizontalDivider(color = CocokColors.GarisTipis)

        BarisJudul("Data")
        Text(
            "${state.catalog.vehicles.size} mobil · ${state.catalog.parts.size} barang · ${state.catalog.fitments.size} kecocokan",
            style = MaterialTheme.typography.bodyLarge,
            color = CocokColors.Teks,
        )

        Spacer(Modifier.height(8.dp))
        TombolGaris("Keluar", onClick = { vm.keluar(); onKeluar() }, warnaTeks = CocokColors.TidakCocokTeks, warnaGaris = CocokColors.TidakCocokTeks)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cocok versi 0.1.0 · data contoh untuk pengembangan",
            style = MaterialTheme.typography.bodySmall,
            color = CocokColors.TeksPendamping,
        )
    }
}

/** Layar "Cari di internet" tahap P1: teks siap salin + formulir terstruktur. */
@Composable
fun CariInternetScreen(vm: CocokViewModel, pertanyaan: String, onSelesai: () -> Unit) {
    var nomorPart by remember { mutableStateOf("") }
    var sumber by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var tersalin by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensi.JarakLayar, vertical = Dimensi.JarakKecil),
        verticalArrangement = Arrangement.spacedBy(Dimensi.JarakAntarTombol),
    ) {
        Text("Cari di internet", style = MaterialTheme.typography.headlineMedium, color = CocokColors.Teks)
        Text(
            "Salin pertanyaan di bawah ke chatbot, lalu tempel jawabannya di sini. Hasilnya disimpan sebagai MUNGKIN COCOK.",
            style = MaterialTheme.typography.bodyLarge,
            color = CocokColors.TeksPendamping,
        )

        Kartu(border = true) {
            Text(pertanyaan, style = MaterialTheme.typography.bodyLarge, color = CocokColors.Teks)
        }

        TombolGaris(
            teks = if (tersalin) "Tersalin" else "Salin pertanyaan",
            onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(pertanyaan))
                tersalin = true
            },
        )

        OutlinedTextField(
            value = nomorPart,
            onValueChange = { nomorPart = it },
            label = { Text("Nomor part yang ditemukan", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        OutlinedTextField(
            value = sumber,
            onValueChange = { sumber = it },
            label = { Text("Sumber (nama situs atau toko)", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )

        TombolUtama(
            teks = "SIMPAN SEBAGAI MUNGKIN",
            aktif = nomorPart.isNotBlank() && sumber.isNotBlank(),
            onClick = {
                vm.terapkanRisetInternet(nomorPart, sumber, onSaved = onSelesai)
            },
        )
        TombolGaris("Nanti saja", onClick = onSelesai)
    }
}
