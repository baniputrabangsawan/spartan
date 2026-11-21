package tech.cocok.app.domain.usecase

import java.math.BigDecimal
import java.time.Instant
import java.util.Locale
import tech.cocok.app.domain.model.CandidateOrigin
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.ComparisonKind
import tech.cocok.app.domain.model.ItemComparison
import tech.cocok.app.domain.model.MatchCandidate
import tech.cocok.app.domain.model.MatchContext
import tech.cocok.app.domain.model.MatchEvidence
import tech.cocok.app.domain.model.MatchItem
import tech.cocok.app.domain.model.MatchKind
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.SpecificationComparison
import tech.cocok.app.domain.model.SpecificationState
import tech.cocok.app.domain.model.TeksFoto

/** Discovery is not fitment verification; only explicit, scoped owner evidence is authoritative. */
class MatchParts {
    val fields: List<Pair<String, String>> = listOf(
        "position" to "Posisi",
        "dimensions_mm" to "Ukuran (mm)",
        "connector" to "Soket",
        "pin_count" to "Jumlah pin",
        "bolt_spacing_mm" to "Jarak baut (mm)",
        "voltage_v" to "Tegangan (V)",
        "thread" to "Ulir",
    )

    private val search = SearchParts()
    private val whitespace = Regex("\\s+")
    private val unknown = setOf("?", "-", "unknown", "tidak tahu", "belum tahu", "belum diketahui", "tidak diketahui")

    fun item(part: Part): MatchItem = MatchItem(
        id = part.id,
        name = part.name,
        partNumber = part.partNumber,
        brand = part.brand,
        categoryId = part.categoryId,
        photo = part.photoKey,
        specifications = part.specifications,
        catalogPartId = part.id,
        linkedCatalogPartId = part.id,
        origin = CandidateOrigin.CATALOG,
    )
    fun discover(
        catalog: Catalog,
        query: String,
        fromPhoto: Boolean = false,
        sourcePartId: String? = null,
        context: MatchContext = MatchContext(),
    ): List<MatchCandidate> {
        if (context.vehicleId != null && catalog.kendaraan(context.vehicleId) == null) return emptyList()
        val found = linkedMapOf<String, MatchCandidate>()
        if (query.isNotBlank()) {
            val parsed = search.cari(catalog, query)
            val scoped = parsed.copy(
                mobil = context.vehicleId?.let { id -> catalog.vehicles.filter { it.id == id } } ?: parsed.mobil,
                tahun = context.year ?: parsed.tahun,
            )
            val hits = if (fromPhoto) search.dariFoto(catalog, TeksFoto(query)) else search.hits(catalog, scoped)
            for (hit in hits) {
                if (hit.part.id != sourcePartId && discoverable(catalog, hit.part.id, context)) {
                    found[hit.part.id] = MatchCandidate(item(hit.part), if (fromPhoto) {
                        "Kandidat dari teks foto; bukan kecocokan bentuk atau bukti pengganti."
                    } else "Kandidat dari pencarian katalog; periksa identitas dan spesifikasi.")
                }
            }
        }
        if (sourcePartId != null && catalog.part(sourcePartId) != null) {
            val groups = catalog.groupParts.filter { it.partId == sourcePartId }.map { it.groupId }.toSet()
            for (link in catalog.groupParts) {
                if (link.groupId !in groups || link.partId == sourcePartId) continue
                val part = catalog.part(link.partId) ?: continue
                if (!discoverable(catalog, part.id, context)) continue
                found[part.id] = MatchCandidate(item(part), "Tercatat dalam grup barang sumber; belum membuktikan pengganti untuk mobil ini.")
            }
        }
        return found.values.toList()
    }

    private fun discoverable(catalog: Catalog, partId: String, context: MatchContext): Boolean {
        if (context.vehicleId == null) return true
        val groups = catalog.groupParts.filter { it.partId == partId }.map { it.groupId }.toSet()
        return catalog.fitments.any { fitment ->
            fitment.groupId in groups && fitment.vehicleId == context.vehicleId &&
                inRange(context.year, fitment.yearFrom, fitment.yearTo) &&
                fitment.constraints.all { (key, value) ->
                    context.variant[key]?.let { normalized(it) == normalized(value) } ?: true
                }
        }
    }

    fun compare(catalog: Catalog, source: MatchItem, candidate: MatchItem, context: MatchContext): ItemComparison {
        val labels = fields.toMap()
        val keys = (labels.keys + source.specifications.keys + candidate.specifications.keys).distinct()
        val rows = keys.map { key ->
            val first = source.specifications[key]?.trim()?.takeIf(::known)
            val second = candidate.specifications[key]?.trim()?.takeIf(::known)
            val state = when {
                first == null || second == null -> SpecificationState.MISSING
                specValue(key, first) == specValue(key, second) -> SpecificationState.SAME
                else -> SpecificationState.DIFFERENT
            }
            SpecificationComparison(key, labels[key] ?: key, first, second, state, physicalCheck(key, labels[key] ?: key))
        }
        val first = boundPart(catalog, source)
        val second = boundPart(catalog, candidate)
        val contextError = contextError(catalog, context, listOfNotNull(first?.id, second?.id))
        val canVerify = first != null && second != null && first.id != second.id && contextError == null
        val checks = buildList {
            rows.filter { it.state != SpecificationState.SAME }.forEach { add(it.check) }
            if (source.categoryId == null || source.categoryId != candidate.categoryId || catalog.kategori(source.categoryId) == null) {
                add("Pastikan jenis barang dan daftar ukuran wajib bersama pemilik; kategori belum sama atau belum diketahui.")
            }
            if (contextError != null) add(contextError)
            if (first == null || second == null) add("Simpan kedua identitas dan spesifikasi ke katalog sebelum mencatat verifikasi pemilik.")
        }.distinct()
        val evidence = if (canVerify) catalog.matchEvidence.lastOrNull { evidence ->
            setOf(evidence.firstPartId, evidence.secondPartId) == setOf(first?.id, second?.id) &&
                evidence.vehicleId == context.vehicleId && evidence.year == context.year &&
                normalizedMap(evidence.variant) == normalizedMap(context.variant) && validEvidence(catalog, evidence)
        } else null
        val differs = rows.any { it.state == SpecificationState.DIFFERENT } ||
            (source.categoryId != null && candidate.categoryId != null && source.categoryId != candidate.categoryId)
        val kind = when {
            evidence?.kind == MatchKind.NOT_REPLACEMENT -> ComparisonKind.REJECTED
            differs -> ComparisonKind.DIFFERENCES
            first != null && second != null && first.id == second.id -> ComparisonKind.SAME_ITEM
            evidence?.kind == MatchKind.SAME_ITEM -> ComparisonKind.SAME_ITEM
            evidence?.kind == MatchKind.DIRECT_REPLACEMENT -> ComparisonKind.VERIFIED_REPLACEMENT
            else -> ComparisonKind.CANDIDATE
        }
        val label = when (kind) {
            ComparisonKind.SAME_ITEM -> "Barang sama"
            ComparisonKind.VERIFIED_REPLACEMENT -> "Pengganti terverifikasi"
            ComparisonKind.REJECTED -> "Bukan pengganti"
            ComparisonKind.DIFFERENCES -> "Ada perbedaan"
            ComparisonKind.CANDIDATE -> "Kandidat — belum terverifikasi"
        }
        val explanation = when (kind) {
            ComparisonKind.SAME_ITEM -> if (first?.id == second?.id && first != null) {
                "Identitas katalog yang sama; bukan jaminan cocok untuk setiap mobil."
            } else "Pemilik mencatat barang sama hanya untuk mobil, tahun, dan varian ini. Data yang kosong tetap perlu dicek."
            ComparisonKind.VERIFIED_REPLACEMENT -> "Pemilik mencatat pengganti langsung hanya untuk mobil, tahun, dan varian ini. Data yang kosong tetap perlu dicek."
            ComparisonKind.REJECTED -> "Pemilik mencatat bukan pengganti untuk mobil, tahun, dan varian ini: ${evidence?.note}"
            ComparisonKind.DIFFERENCES -> "Perbedaan spesifikasi tetap terlihat dan harus diperiksa fisik sebelum digunakan." +
                if (evidence != null) " Ada catatan pemilik untuk konteks ini: ${evidence.note}" else ""
            ComparisonKind.CANDIDATE -> "Foto, nomor part, grup, dan spesifikasi yang sama hanya petunjuk; belum membuktikan kecocokan mobil."
        }
        return ItemComparison(kind, label, explanation, rows, checks, canVerify)
    }

    fun record(catalog: Catalog, evidence: MatchEvidence): Catalog {
        require(validEvidence(catalog, evidence)) { "Verifikasi perlu pemilik, dua barang tersimpan berbeda, alasan, penulis, waktu, dan konteks mobil/tahun/varian lengkap yang valid." }
        require(catalog.matchEvidence.none { it.id == evidence.id }) { "ID riwayat verifikasi sudah dipakai." }
        return catalog.copy(matchEvidence = catalog.matchEvidence + evidence)
    }

    private fun validEvidence(catalog: Catalog, evidence: MatchEvidence): Boolean =
        evidence.role == Role.PEMILIK && evidence.id.isNotBlank() && evidence.note.isNotBlank() &&
            evidence.by.isNotBlank() && runCatching { Instant.parse(evidence.createdAt) }.isSuccess &&
            evidence.firstPartId != evidence.secondPartId && catalog.part(evidence.firstPartId) != null &&
            catalog.part(evidence.secondPartId) != null && contextError(
                catalog,
                MatchContext(evidence.vehicleId, evidence.year, evidence.variant),
                listOf(evidence.firstPartId, evidence.secondPartId),
            ) == null

    private fun contextError(catalog: Catalog, context: MatchContext, partIds: List<String>): String? {
        val vehicle = context.vehicleId?.let(catalog::kendaraan)
            ?: return "Pilih mobil tersimpan untuk verifikasi."
        val year = context.year ?: return "Isi tahun mobil untuk verifikasi."
        if (year !in 1886..2100 || !inRange(year, vehicle.yearFrom, vehicle.yearTo)) return "Tahun harus berada dalam rentang mobil yang dipilih."
        if (context.variant.isEmpty() || context.variant.any { !known(it.key) || !known(it.value) }) {
            return "Isi varian mobil yang diketahui, bukan nilai kosong atau belum tahu."
        }
        val groups = catalog.groupParts.filter { it.partId in partIds }.map { it.groupId }.toSet()
        val constraints = catalog.fitments.filter {
            it.groupId in groups && it.vehicleId == vehicle.id && inRange(year, it.yearFrom, it.yearTo)
        }.flatMap { it.constraints.keys }
        val required = vehicle.attributes.keys + constraints
        if (required.any { key -> context.variant[key]?.let(::known) != true }) return "Lengkapi varian wajib: ${required.distinct().joinToString(", ")}."
        if (vehicle.attributes.any { (key, value) -> !known(value) || normalized(context.variant[key].orEmpty()) != normalized(value) }) {
            return "Varian harus sesuai data mobil yang dipilih; periksa atribut mobil."
        }
        return null
    }

    // Edited input must not inherit verification from the catalog identity it used to represent.
    private fun boundPart(catalog: Catalog, value: MatchItem): Part? {
        val part = value.catalogPartId?.let(catalog::part) ?: return null
        val stored = item(part)
        return part.takeIf {
            value.id == stored.id && value.origin == CandidateOrigin.CATALOG &&
                value.name == stored.name && value.partNumber == stored.partNumber && value.brand == stored.brand &&
                value.categoryId == stored.categoryId && value.specifications == stored.specifications
        }
    }

    private fun inRange(year: Int?, from: Int?, to: Int?): Boolean =
        year == null || (from == null || year >= from) && (to == null || year <= to)

    private fun known(value: String): Boolean = normalized(value).let { it.isNotEmpty() && it !in unknown }
    private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT).replace(whitespace, " ")
    private fun normalizedMap(value: Map<String, String>): Map<String, String> = value.mapValues { normalized(it.value) }

    private fun specValue(key: String, value: String): String {
        val text = normalized(value)
        val unit = when (key) {
            "bolt_spacing_mm" -> "mm"
            "voltage_v" -> "v"
            else -> null
        }
        val scalar = if (unit != null) text.removeSuffix(unit).trim() else text
        return if (key in setOf("bolt_spacing_mm", "voltage_v", "pin_count") && scalar.matches(Regex("[+-]?\\d+(?:\\.\\d+)?"))) {
            BigDecimal(scalar).stripTrailingZeros().toPlainString()
        } else text
    }

    private fun physicalCheck(key: String, label: String): String = when (key) {
        "position" -> "Cek sisi kiri/kanan serta posisi depan/belakang pada kedua barang."
        "dimensions_mm" -> "Ukur panjang, lebar, tinggi, dan diameter kedua barang dalam mm."
        "connector" -> "Cocokkan bentuk, pengunci, dan orientasi soket kedua barang."
        "pin_count" -> "Hitung pin dan cocokkan susunan serta fungsi pin kedua barang."
        "bolt_spacing_mm" -> "Ukur jarak pusat baut dan cocokkan jumlah serta posisi lubang dalam mm."
        "voltage_v" -> "Periksa label tegangan dan kebutuhan rangkaian kendaraan dalam volt."
        "thread" -> "Ukur diameter, pitch, dan arah ulir kedua barang."
        else -> "Periksa $label pada kedua barang menggunakan ukuran atau label fisik."
    }
}
