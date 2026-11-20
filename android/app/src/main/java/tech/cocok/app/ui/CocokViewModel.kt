package tech.cocok.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.cocok.app.CocokApplication
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.CompareOutcome
import tech.cocok.app.domain.model.Confirmation
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.FitmentGroup
import tech.cocok.app.domain.model.GroupPart
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Sumber
import tech.cocok.app.domain.model.TeksFoto
import tech.cocok.app.domain.model.Vehicle
import tech.cocok.app.domain.search.ParsedQuery
import tech.cocok.app.domain.model.MatchEvidence
import tech.cocok.app.domain.model.MatchItem
import tech.cocok.app.domain.usecase.CompareParts
import tech.cocok.app.domain.usecase.SearchParts
import java.time.Instant
import tech.cocok.app.domain.usecase.MatchParts
import java.util.UUID

data class Riwayat(val teks: String, val waktu: Long)
data class KonteksRiset(val categoryId: String, val vehicleId: String?)

data class AppUiState(
    val masuk: Boolean = false,
    val namaPengguna: String = "",
    val role: Role = Role.KASIR,
    val catalog: Catalog = Catalog(),
    val daring: Boolean = true,
    val riwayat: List<Riwayat> = emptyList(),
    val pesan: String? = null,
    val riset: KonteksRiset? = null,
    val memuat: Boolean = true,
    val siap: Boolean = false,
    val masalah: String? = null,
    val pendingWrites: Int = 0,
)

/** UI publishes a changed catalog only after the Room transaction commits. */
class CocokViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CocokApplication).catalogRepository
    private val pencarian = SearchParts()
    private val pembanding = CompareParts()
    private val padanan = MatchParts()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init { muatData() }

    fun muatData() {
        viewModelScope.launch {
            _state.update { it.copy(memuat = true, masalah = null) }
            try {
                val catalog = repository.load()
                _state.update { it.copy(catalog = catalog, memuat = false, siap = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(memuat = false, masalah = "Data di HP belum bisa dibuka. Coba lagi; data tidak dihapus.")
                }
            }
        }
    }

    private fun simpan(
        pesan: String,
        onSaved: () -> Unit = {},
        transform: (Catalog) -> Catalog,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(pendingWrites = it.pendingWrites + 1, masalah = null) }
            try {
                val catalog = repository.update(transform)
                _state.update { it.copy(catalog = catalog, pesan = pesan) }
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update {
                    it.copy(masalah = "Belum tersimpan di HP. Data sebelumnya tetap ada; periksa isian lalu simpan lagi.")
                }
            } finally {
                _state.update { it.copy(pendingWrites = it.pendingWrites - 1) }
            }
        }
    }

    fun masuk(nama: String, sebagai: Role) = _state.update {
        it.copy(masuk = true, namaPengguna = nama.ifBlank { sebagai.label }, role = sebagai,
            pesan = "Mode demo lokal. Belum terhubung ke Cloudflare.")
    }

    fun keluar() = _state.update {
        it.copy(masuk = false, namaPengguna = "", role = Role.KASIR, pesan = null, riset = null)
    }

    /** Discovery is not verification: a new item always starts in its own MUNGKIN group. */
    fun simpanItemSebagaiBarang(item: MatchItem, onSaved: (Part) -> Unit = {}) {
        val categoryId = item.categoryId ?: _state.value.catalog.categories.firstOrNull()?.id
        if (categoryId == null || item.name.isBlank()) {
            _state.update { it.copy(masalah = "Pilih jenis barang dan isi nama sebelum menyimpan.") }
            return
        }
        val part = Part(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            name = item.name.trim(),
            brand = item.brand?.trim()?.ifBlank { null },
            partNumber = item.partNumber?.trim()?.ifBlank { null },
            isOriginal = item.brand.isNullOrBlank(),
            photoKey = item.photo,
            specifications = item.specifications,
        )
        val group = FitmentGroup(UUID.randomUUID().toString(), categoryId, part.name)
        simpan(
            pesan = "${part.name} tersimpan di HP sebagai MUNGKIN. Belum dikaitkan ke mobil.",
            onSaved = { onSaved(part) },
            transform = {
                require(it.kategori(categoryId) != null)
                it.copy(
                    parts = it.parts + part,
                    groups = it.groups + group,
                    groupParts = it.groupParts + GroupPart(group.id, part.id, Status.MUNGKIN),
                )
            },
        )
    }

    fun simpanSpesifikasi(partId: String, specifications: Map<String, String>) {
        if (_state.value.catalog.part(partId) == null) return
        simpan("Spesifikasi tersimpan di HP.") {
            it.copy(parts = it.parts.map { part ->
                if (part.id == partId) part.copy(specifications = specifications) else part
            })
        }
    }

    /** Owner-only, scoped evidence. Refusal keeps the previous catalog untouched. */
    fun simpanBuktiPadanan(evidence: MatchEvidence, onSaved: () -> Unit = {}) {
        val before = _state.value.catalog
        val after = try {
            padanan.record(before, evidence)
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(masalah = error.message ?: "Verifikasi belum bisa disimpan.") }
            return
        }
        simpan(
            pesan = "Catatan padanan tersimpan di HP untuk mobil, tahun, dan varian ini.",
            onSaved = onSaved,
            transform = { after },
        )
    }

    fun gantiPeran(role: Role) = _state.update { it.copy(role = role) }
    fun aturDaring(daring: Boolean) = _state.update { it.copy(daring = daring) }
    fun bersihkanPesan() = _state.update { it.copy(pesan = null) }
    fun tutupMasalah() = _state.update { it.copy(masalah = null) }
    fun catatPencarian(teks: String) = _state.update { s ->
        if (teks.isBlank()) s else s.copy(
            riwayat = (listOf(Riwayat(teks, System.currentTimeMillis())) + s.riwayat.filter { it.teks != teks }).take(6),
        )
    }
    fun hapusRiwayat() = _state.update { it.copy(riwayat = emptyList()) }

    fun parse(teks: String): ParsedQuery = pencarian.cari(_state.value.catalog, teks)
    fun cari(teks: String): List<SearchHit> {
        val catalog = _state.value.catalog
        return pencarian.hits(catalog, pencarian.cari(catalog, teks))
    }
    fun hasilUntuk(kendaraan: Vehicle?, tahun: Int?, kategori: PartCategory?): List<SearchHit> =
        cari(listOfNotNull(kendaraan?.brand, kendaraan?.model, kendaraan?.generation,
            tahun?.toString(), kategori?.name).joinToString(" "))
    fun pengganti(part: Part): List<SearchHit> = pencarian.pengganti(_state.value.catalog, part)
    fun kategoriSering(): List<PartCategory> = pencarian.kategoriSering(_state.value.catalog)
    fun kendaraanSering(): List<Vehicle> = pencarian.kendaraanSering(_state.value.catalog)
    fun bandingkan(kategori: PartCategory, a: Vehicle, tahunA: Int, b: Vehicle, tahunB: Int): CompareOutcome =
        pembanding.bandingkan(_state.value.catalog, kategori, a, tahunA, b, tahunB)

    /** Refresh a selected card from committed data, retaining its vehicle context. */
    fun perbaruiHit(hit: SearchHit): SearchHit = hit.perbarui(_state.value.catalog)

    /**
     * Mencocokkan tulisan hasil foto ke database toko.
     *
     * Nomor part dicari lebih dulu karena paling menentukan; bila tidak ada,
     * seluruh baris tulisan dipakai sebagai kata pencarian. Label status tetap
     * aturan biasa: foto tidak bisa menaikkan keyakinan sebuah kecocokan.
     */
    fun cariDariTeksFoto(terbaca: TeksFoto): List<SearchHit> =
        pencarian.dariFoto(_state.value.catalog, terbaca)

    fun konfirmasi(fitmentId: String, cocok: Boolean, catatan: String? = null) {
        val s = _state.value
        val fitment = s.catalog.fitments.firstOrNull { it.id == fitmentId } ?: return
        val confirmation = Confirmation(
            id = UUID.randomUUID().toString(), fitmentId = fitmentId, groupId = fitment.groupId,
            oleh = s.namaPengguna.ifBlank { s.role.label }, role = s.role, hasil = cocok,
            catatan = catatan, waktu = Instant.now().toString(),
        )
        simpan("Konfirmasi tersimpan di HP. Belum dikirim ke server.") { it.tambahKonfirmasi(confirmation) }
    }

    fun tetapkanStatus(fitmentId: String, status: Status): Boolean {
        val role = _state.value.role
        if (!role.bisaMenetapkanStatus || !status.tersimpan) return false
        val waktu = Instant.now().toString()
        simpan("Status tersimpan di HP: ${status.name.lowercase()}.") {
            it.tetapkanStatus(fitmentId, status, role, waktu)
        }
        return true
    }

    fun tambahKendaraan(
        brand: String, model: String, generation: String, dari: Int?, sampai: Int?,
        onSaved: () -> Unit = {},
    ) {
        val vehicle = Vehicle(
            UUID.randomUUID().toString(), brand.trim(), model.trim(),
            generation.trim().ifBlank { null }, dari, sampai, verified = false,
        )
        simpan("${vehicle.nama} tersimpan di HP.", onSaved) {
            require(brand.isNotBlank() && model.isNotBlank())
            require(dari == null || sampai == null || dari <= sampai)
            it.copy(vehicles = it.vehicles + vehicle)
        }
    }

    fun tambahBarangDanKelompok(
        nama: String, kategoriId: String, nomorPart: String, merek: String,
        onSaved: () -> Unit = {},
    ) {
        val part = Part(
            UUID.randomUUID().toString(), kategoriId, nama.trim(), merek.trim().ifBlank { null },
            nomorPart.trim().ifBlank { null }, isOriginal = merek.isBlank(),
        )
        val group = FitmentGroup(UUID.randomUUID().toString(), kategoriId, nama.trim())
        simpan("${part.name} tersimpan di HP.", onSaved) {
            require(nama.isNotBlank() && it.kategori(kategoriId) != null)
            it.copy(
                parts = it.parts + part, groups = it.groups + group,
                groupParts = it.groupParts + GroupPart(group.id, part.id, Status.MUNGKIN),
            )
        }
    }

    fun hubungkan(
        part: Part, kendaraan: Vehicle, dari: Int?, sampai: Int?, onSaved: () -> Unit = {},
    ) {
        val id = UUID.randomUUID().toString()
        simpan("Kecocokan MUNGKIN tersimpan di HP.", onSaved) { catalog ->
            require(dari == null || sampai == null || dari <= sampai)
            val groupId = requireNotNull(catalog.groupParts.firstOrNull { it.partId == part.id }?.groupId)
            require(catalog.kendaraan(kendaraan.id) != null)
            catalog.copy(fitments = catalog.fitments + Fitment(
                id, groupId, kendaraan.id, dari, sampai, status = Status.MUNGKIN, sumber = Sumber.TOKO,
            ))
        }
    }

    fun mulaiRiset(categoryId: String, vehicleId: String? = null) =
        _state.update { it.copy(riset = KonteksRiset(categoryId, vehicleId)) }
    fun selesaiRiset() = _state.update { it.copy(riset = null) }
    fun pertanyaanRiset(): String {
        val s = _state.value
        val kategori = s.riset?.categoryId?.let { s.catalog.kategori(it) }?.nama ?: "barang"
        val kendaraan = s.riset?.vehicleId?.let { s.catalog.kendaraan(it) }?.namaDanTahun ?: "mobil"
        return "Apakah $kategori untuk $kendaraan bisa diganti barang lain? " +
            "Sebutkan nomor part asli, padanan, tahun, varian mobil, dan sumbernya."
    }

    fun terapkanRisetInternet(nomorPart: String, sumber: String, onSaved: () -> Unit = {}) {
        val context = _state.value.riset ?: return
        val vehicle = context.vehicleId?.let { _state.value.catalog.kendaraan(it) }
        val category = _state.value.catalog.kategori(context.categoryId) ?: return
        val part = Part(
            UUID.randomUUID().toString(), category.id, category.nama,
            partNumber = nomorPart.trim(), notes = "Sumber internet: ${sumber.trim()}",
        )
        // Research must not silently join an unrelated existing equivalence group.
        val group = FitmentGroup(UUID.randomUUID().toString(), category.id, "${category.nama} ${part.partNumber}")
        val fitment = vehicle?.let {
            Fitment(UUID.randomUUID().toString(), group.id, it.id, it.yearFrom, it.yearTo,
                status = Status.MUNGKIN, sumber = Sumber.INTERNET)
        }
        simpan("Riset tersimpan di HP sebagai MUNGKIN. Cek barang sebelum dijual.", {
            selesaiRiset()
            onSaved()
        }) {
            require(nomorPart.isNotBlank() && sumber.isNotBlank())
            it.copy(
                parts = it.parts + part, groups = it.groups + group,
                groupParts = it.groupParts + GroupPart(group.id, part.id, Status.MUNGKIN),
                fitments = if (fitment == null) it.fitments else it.fitments + fitment,
            )
        }
    }
}
