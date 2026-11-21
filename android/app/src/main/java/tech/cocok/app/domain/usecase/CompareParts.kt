package tech.cocok.app.domain.usecase

import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.CompareAnswer
import tech.cocok.app.domain.model.CompareOutcome
import tech.cocok.app.domain.model.CompareSide
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.Vehicle

/**
 * Aturan Bandingkan (README §6.3).
 *
 * Aturan yang tidak boleh dilanggar: "tidak ada data" TIDAK menghasilkan BEDA.
 * BEDA hanya jika kedua sisi punya data PASTI dan grup barangnya tidak beririsan.
 */
class CompareParts {

    fun bandingkan(
        catalog: Catalog,
        kategori: PartCategory,
        kendaraanA: Vehicle,
        tahunA: Int,
        kendaraanB: Vehicle,
        tahunB: Int,
    ): CompareOutcome {
        val sisiA = sisi(catalog, kategori, kendaraanA, tahunA)
        val sisiB = sisi(catalog, kategori, kendaraanB, tahunB)

        val grupA = sisiA.grup.map { it.id }.toSet()
        val grupB = sisiB.grup.map { it.id }.toSet()
        val irisan = grupA intersect grupB

        val jawaban: CompareAnswer
        val catatan: String?
        when {
            irisan.isNotEmpty() -> {
                jawaban = CompareAnswer.SAMA
                catatan = "Kedua mobil memakai kelompok barang yang sama, jadi bisa saling menggantikan."
            }

            sisiA.status == Status.PASTI && sisiB.status == Status.PASTI -> {
                jawaban = CompareAnswer.BEDA
                catatan = "Kedua mobil sudah punya data yang pasti, dan barangnya tidak sama."
            }

            else -> {
                jawaban = CompareAnswer.BELUM_TAHU
                catatan = "Belum ada data yang cukup. Ini bukan berarti barangnya beda."
            }
        }

        val partBersama = if (irisan.isEmpty()) emptyList() else {
            irisan.flatMap { catalog.partDiGrup(it) }.distinctBy { it.id }
        }

        return CompareOutcome(
            jawaban = jawaban,
            kategori = kategori,
            sisiA = sisiA,
            sisiB = sisiB,
            partBersama = partBersama,
            catatan = catatan,
        )
    }

    private fun sisi(
        catalog: Catalog,
        kategori: PartCategory,
        kendaraan: Vehicle,
        tahun: Int,
    ): CompareSide {
        val fitments = catalog.fitmentUntuk(kendaraan.id, tahun, kategori.id)
        val grupIds = fitments.map { it.groupId }.toSet()
        val grup = grupIds.mapNotNull { catalog.grup(it) }
        // Single pass over groupParts; the old per-group filter rescanned the whole
        // list for each group (~700 ms on a 20k-part catalog).
        val bagian = catalog.groupParts
            .filter { it.groupId in grupIds }
            .mapNotNull { catalog.part(it.partId) }
            .distinctBy { it.id }
        val status = statusSisi(fitments, grupIds, catalog)
        return CompareSide(
            kendaraan = kendaraan,
            tahun = tahun,
            grup = grup,
            bagian = bagian,
            status = status,
        )
    }

    /**
     * Status satu sisi = status terendah di antara data yang ada pada sisi itu.
     * Tidak ada data sama sekali -> BELUM_ADA_DATA (bukan TIDAK_COCOK).
     */
    private fun statusSisi(fitments: List<Fitment>, groupIds: Set<String>, catalog: Catalog): Status {
        if (fitments.isEmpty()) return Status.BELUM_ADA_DATA
        val dariFitment = fitments.map { it.status }
        val dariPart = catalog.groupParts.filter { it.groupId in groupIds }.map { it.status }
        return Status.terendah(dariFitment + dariPart)
    }
}
