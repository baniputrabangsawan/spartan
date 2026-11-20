package tech.cocok.app.domain.model

/**
 * Empat label hasil (DESIGN.md §3).
 *
 * [BELUM_ADA_DATA] bukan nilai yang disimpan di database; ia adalah jawaban
 * turunan saat tidak ada kecocokan yang tercatat. Karena itu ia tidak
 * diperlakukan sama dengan [TIDAK_COCOK] (PRD.md §7 aturan 4).
 */
enum class Status {
    PASTI,
    MUNGKIN,
    TIDAK_COCOK,
    BELUM_ADA_DATA;

    /** Nilai yang boleh disimpan di kolom `status` (D1 CHECK). */
    val tersimpan: Boolean get() = this != BELUM_ADA_DATA

    /** Dipakai saat menggabungkan dua status: yang terendah menang. */
    val peringkat: Int
        get() = when (this) {
            PASTI -> 3
            MUNGKIN -> 2
            BELUM_ADA_DATA -> 1
            TIDAK_COCOK -> 0
        }

    companion object {
        /** Status terendah dari sekumpulan status; kosong berarti belum ada data. */
        fun terendah(dari: Collection<Status>): Status =
            dari.minByOrNull { it.peringkat } ?: BELUM_ADA_DATA

        /** Status tertinggi (paling yakin) dari sekumpulan status. */
        fun tertinggi(dari: Collection<Status>): Status =
            dari.maxByOrNull { it.peringkat } ?: BELUM_ADA_DATA

        /**
         * Menggabungkan status yang **sudah diketahui**. "Belum ada data" bukan
         * data, jadi ia tidak boleh menurunkan status yang sudah tercatat —
         * itulah yang membuat pengganti berlabel MUNGKIN sempat tampil
         * BELUM ADA DATA. Kalau tidak ada satu pun yang diketahui, jawabannya
         * memang BELUM_ADA_DATA.
         */
        fun terendahDiketahui(dari: Collection<Status>): Status {
            val diketahui = dari.filter { it.tersimpan }
            return if (diketahui.isEmpty()) BELUM_ADA_DATA else terendah(diketahui)
        }

        fun dariKunci(kunci: String?): Status = when (kunci) {
            "pasti" -> PASTI
            "mungkin" -> MUNGKIN
            "tidak_cocok" -> TIDAK_COCOK
            else -> BELUM_ADA_DATA
        }
    }
}

enum class Role {
    PEMILIK,
    KASIR;

    val label: String get() = if (this == PEMILIK) "Pemilik" else "Kasir"
    val bisaMenetapkanStatus: Boolean get() = this == PEMILIK
}

/** Sumber data kecocokan (kolom `sources.kind`). */
enum class Sumber { INTERNET, KATALOG, TOKO }

data class Vehicle(
    val id: String,
    val brand: String,
    val model: String,
    val generation: String?,
    val yearFrom: Int?,
    val yearTo: Int?,
    val attributes: Map<String, String> = emptyMap(),
    val verified: Boolean = true,
) {
    /** "Hilux Reborn" — nama pendek untuk chip dan daftar. */
    val nama: String = listOfNotNull(brand, model, generation).joinToString(" ")

    /** "Hilux Reborn 2016–2020" */
    val namaDanTahun: String =
        if (yearFrom != null && yearTo != null) "$nama $yearFrom–$yearTo" else nama

    fun cocokTahun(tahun: Int?): Boolean {
        if (tahun == null) return true
        val dari = yearFrom ?: return true
        val sampai = yearTo ?: return true
        return tahun in dari..sampai
    }

    val varian: String
        get() = attributes.entries.joinToString(", ") { (k, v) -> "$k: $v" }
}

data class PartCategory(
    val id: String,
    val name: String,
    val position: String? = null,
) {
    val nama: String = if (position.isNullOrBlank()) name else "$name $position"
}

data class Part(
    val id: String,
    val categoryId: String,
    val name: String,
    val brand: String? = null,
    val partNumber: String? = null,
    val isOriginal: Boolean = false,
    val photoKey: String? = null,
    val notes: String? = null,
    val specifications: Map<String, String> = emptyMap(),
)

data class FitmentGroup(
    val id: String,
    val categoryId: String,
    val label: String,
    val notes: String? = null,
)

data class GroupPart(
    val groupId: String,
    val partId: String,
    val status: Status,
)

data class Fitment(
    val id: String,
    val groupId: String,
    val vehicleId: String,
    val yearFrom: Int?,
    val yearTo: Int?,
    val constraints: Map<String, String> = emptyMap(),
    val status: Status,
    val sumber: Sumber = Sumber.TOKO,
    val confirmedByRole: Role? = null,
    val confirmedAt: String? = null,
) {
    fun cocokTahun(tahun: Int?): Boolean {
        if (tahun == null) return true
        val dari = yearFrom ?: return true
        val sampai = yearTo ?: return true
        return tahun in dari..sampai
    }
}

data class Confirmation(
    val id: String,
    val fitmentId: String,
    val groupId: String,
    val oleh: String,
    val role: Role,
    val hasil: Boolean,
    val catatan: String? = null,
    val waktu: String,
)

/** Satu kartu di layar HASIL. */
data class SearchHit(
    val part: Part,
    val group: FitmentGroup,
    val status: Status,
    val matches: List<Fitment>,
    val kendaraan: List<Vehicle>,
    val catatan: String?,
    /** True hanya ketika mobil dipilih/disebut; daftar fitment lain bukan konteks jawaban. */
    val vehicleSpecific: Boolean = false,
) {
    /** Memperbarui detail tanpa menggabungkan fitment mobil lain ke jawaban. */
    fun perbarui(catalog: Catalog): SearchHit {
        val terbaru = matches.mapNotNull { old -> catalog.fitments.firstOrNull { it.id == old.id } }
        val statusPart = catalog.statusPart(group.id, part.id)
        return copy(
            part = catalog.part(part.id) ?: part,
            matches = terbaru,
            kendaraan = terbaru.mapNotNull { catalog.kendaraan(it.vehicleId) }.distinctBy { it.id },
            status = when {
                !vehicleSpecific -> statusPart
                terbaru.isEmpty() -> Status.BELUM_ADA_DATA
                else -> Status.terendahDiketahui(terbaru.map { it.status } + statusPart)
            },
        )
    }
}

enum class CompareAnswer { SAMA, BEDA, BELUM_TAHU }

data class CompareSide(
    val kendaraan: Vehicle,
    val tahun: Int,
    val grup: List<FitmentGroup>,
    val bagian: List<Part>,
    val status: Status,
)

data class CompareOutcome(
    val jawaban: CompareAnswer,
    val kategori: PartCategory,
    val sisiA: CompareSide,
    val sisiB: CompareSide,
    val partBersama: List<Part>,
    val catatan: String?,
)
