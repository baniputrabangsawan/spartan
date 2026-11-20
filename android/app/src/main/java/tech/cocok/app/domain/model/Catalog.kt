package tech.cocok.app.domain.model

/**
 * Satu salinan data lengkap di HP (ARCHITECTURE.md §1).
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
    fun kategori(id: String): PartCategory? = categories.firstOrNull { it.id == id }
    fun kendaraan(id: String): Vehicle? = vehicles.firstOrNull { it.id == id }
    fun grup(id: String): FitmentGroup? = groups.firstOrNull { it.id == id }
    fun part(id: String): Part? = parts.firstOrNull { it.id == id }

    fun partDiGrup(groupId: String): List<Part> =
        groupParts.filter { it.groupId == groupId }.mapNotNull { part(it.partId) }

    fun statusPart(groupId: String, partId: String): Status =
        groupParts.firstOrNull { it.groupId == groupId && it.partId == partId }?.status ?: Status.BELUM_ADA_DATA

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

    /** Hanya pemilik yang boleh menetapkan status final (PRD.md §7 aturan 2). */
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
