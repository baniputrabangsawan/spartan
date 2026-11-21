package tech.cocok.app.domain.model

/**
 * Satu salinan data lengkap di HP (README §4).
 * Semua use case menerima objek ini; tidak ada akses jaringan di lapisan domain.
 */
data class Catalog(
    val vehicles: List<Vehicle> = emptyList(),
    val categories: List<PartCategory> = emptyList(),
    val parts: List<Part> = emptyList(),
    val groups: List<FitmentGroup> = emptyList(),
    val groupParts: List<GroupPart> = emptyList(),
    val fitments: List<Fitment> = emptyList(),
    val synonyms: Map<String, String> = emptyMap(),
    val confirmations: List<Confirmation> = emptyList(),
    val matchEvidence: List<MatchEvidence> = emptyList(),
) {
    // Derived lookup indexes, built once per immutable Catalog instance. Id lookups
    // used linear scans and dominated search/compare on a 20k-part catalog.
    private val grupById: Map<String, FitmentGroup> by lazy { groups.associateBy { it.id } }
    private val partById: Map<String, Part> by lazy { parts.associateBy { it.id } }
    private val kendaraanById: Map<String, Vehicle> by lazy { vehicles.associateBy { it.id } }
    private val kategoriById: Map<String, PartCategory> by lazy { categories.associateBy { it.id } }
    private val statusPerGrupPart: Map<Pair<String, String>, Status> by lazy {
        groupParts.associateTo(HashMap(groupParts.size)) { (it.groupId to it.partId) to it.status }
    }
    private val grupPerPart: Map<String, String> by lazy {
        groupParts.associateTo(HashMap(groupParts.size)) { it.partId to it.groupId }
    }
    private val partsPerGrup: Map<String, List<Part>> by lazy {
        groupParts.groupBy({ it.groupId }, { it.partId })
            .mapValues { (_, ids) -> ids.mapNotNull { partById[it] } }
    }

    fun kategori(id: String): PartCategory? = kategoriById[id]
    fun kendaraan(id: String): Vehicle? = kendaraanById[id]
    fun grup(id: String): FitmentGroup? = grupById[id]
    fun part(id: String): Part? = partById[id]

    fun partDiGrup(groupId: String): List<Part> = partsPerGrup[groupId].orEmpty()

    /** Grup tempat sebuah barang tercatat, bila ada. */
    fun grupDariPart(partId: String): String? = grupPerPart[partId]

    fun statusPart(groupId: String, partId: String): Status =
        statusPerGrupPart[groupId to partId] ?: Status.BELUM_ADA_DATA

    /** Kecocokan untuk satu mobil + tahun, hanya pada satu jenis barang. */
    fun fitmentUntuk(vehicleId: String, tahun: Int?, categoryId: String?): List<Fitment> =
        fitments.filter { f ->
            f.vehicleId == vehicleId &&
                f.cocokTahun(tahun) &&
                (categoryId == null || grup(f.groupId)?.categoryId == categoryId)
        }

    /** Semua mobil yang tercatat memakai grup ini, beserta rentang tahunnya. */
    fun kendaraanUntukGrup(groupId: String): List<Pair<Vehicle, Fitment>> =
        fitments.filter { it.groupId == groupId }
            .mapNotNull { f -> kendaraan(f.vehicleId)?.let { it to f } }
            .sortedWith(compareBy({ it.first.brand }, { it.first.model }, { it.second.yearFrom ?: 0 }))

    fun statusGrup(groupId: String): Status {
        val dariFitment = fitments.filter { it.groupId == groupId }.map { it.status }
        val dariPart = groupParts.filter { it.groupId == groupId }.map { it.status }
        return Status.tertinggi(dariFitment + dariPart)
    }

    fun tambahKonfirmasi(konfirmasi: Confirmation): Catalog {
        val fitment = fitments.firstOrNull { it.id == konfirmasi.fitmentId }
        val fitmentBaru = if (fitment == null) fitments else fitments.map {
            if (it.id == konfirmasi.fitmentId) {
                // Kasir hanya bisa mengusulkan; status tetap 'mungkin' sampai pemilik menetapkan.
                if (konfirmasi.hasil && it.status == Status.PASTI) it
                else if (!konfirmasi.hasil && konfirmasi.role == Role.PEMILIK) {
                    it.copy(status = Status.TIDAK_COCOK, confirmedByRole = Role.PEMILIK, confirmedAt = konfirmasi.waktu)
                } else it.copy(confirmedByRole = konfirmasi.role, confirmedAt = konfirmasi.waktu)
            } else it
        }
        return copy(fitments = fitmentBaru, confirmations = confirmations + konfirmasi)
    }

    /** Hanya pemilik yang boleh menetapkan status final (README §6.4). */
    fun tetapkanStatus(fitmentId: String, status: Status, oleh: Role, waktu: String): Catalog {
        if (!oleh.bisaMenetapkanStatus) return this
        if (!status.tersimpan) return this
        return copy(
            fitments = fitments.map {
                if (it.id == fitmentId) {
                    it.copy(status = status, confirmedByRole = Role.PEMILIK, confirmedAt = waktu)
                } else it
            },
        )
    }
}
