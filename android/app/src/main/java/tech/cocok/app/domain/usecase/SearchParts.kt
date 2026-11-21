package tech.cocok.app.domain.usecase

import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.PartCategory
import tech.cocok.app.domain.model.SearchHit
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.TeksFoto
import tech.cocok.app.domain.model.Vehicle
import tech.cocok.app.domain.search.HitOrdering
import tech.cocok.app.domain.search.ParsedQuery
import tech.cocok.app.domain.search.QueryParser
import tech.cocok.app.domain.search.TextMatch

/**
 * Satu pencarian lengkap di HP (README §6.2):
 * mobil + tahun + kategori -> fitments -> grup -> barang, urut status.
 *
 * Bila mobil atau kategori belum jelas, hasilnya tetap kandidat yang bisa
 * diketuk — tidak pernah galat (README §7.5).
 */
class SearchParts {

    fun cari(catalog: Catalog, teks: String): ParsedQuery =
        QueryParser(catalog.vehicles, catalog.categories, catalog.synonyms).parse(teks)

    /**
     * Aturan yang menentukan benar/tidaknya jawaban:
     *
     *  - Bila pengguna **menyebut mobil**, hanya kelompok yang punya kecocokan
     *    untuk mobil itu yang boleh muncul. Tanpa aturan ini, barang Vigo ikut
     *    tampil sebagai "PASTI COCOK" saat yang dicari Hilux Reborn — kesalahan
     *    yang langsung berujung salah jual.
     *  - Bila pengguna **tidak** menyebut mobil, semua kelompok yang cocok kata
     *    ditampilkan (mode menjelajah), dan labelnya mengikuti status barang.
     */
    fun hits(catalog: Catalog, parsed: ParsedQuery): List<SearchHit> {
        // Nomor part dicari sebagai satu kesatuan lebih dulu. Pemecahan kata
        // memotong "81560-0K290" menjadi "81560" dan "0K290", dan potongan
        // itu bisa menunjuk barang lain yang berbeda.
        cariNomorPart(catalog, parsed)?.let { return it }

        val mobilDisebut = parsed.mobil.isNotEmpty()
        val mobil = if (mobilDisebut) parsed.mobil else catalog.vehicles
        val kategoriIds = parsed.kategori.map { it.id }.toSet()

        val kandidatGrup = catalog.groups.filter { g ->
            kategoriIds.isEmpty() || g.categoryId in kategoriIds
        }

        val hits = kandidatGrup.mapNotNull { grup ->
            val fitments = catalog.fitments.filter { f ->
                f.groupId == grup.id &&
                    mobil.any { m -> m.id == f.vehicleId && f.cocokTahun(parsed.tahun) }
            }
            // Mobil disebut tapi tidak ada kecocokan untuk mobil itu -> bukan hasil.
            if (mobilDisebut && fitments.isEmpty()) return@mapNotNull null
            if (parsed.kataBarang.isNotEmpty() && !cocokTeks(catalog, grup, parsed)) return@mapNotNull null
            grupKeHits(catalog, grup.id, fitments, mobilDisebut, parsed.sisaKata)
        }.flatten()

        // Tanpa mobil dan tanpa kata yang cocok, jangan kembalikan kosong:
        // tampilkan seluruh kelompok yang cocok kata supaya bisa dipilih.
        val hasil = if (hits.isEmpty() && !mobilDisebut && parsed.kataBarang.isNotEmpty()) {
            cariTeksBebas(catalog, parsed.kataBarang)
        } else {
            hits
        }

        return HitOrdering.urutkan(hasil.distinctBy { it.part.id })
    }

    /** Nomor part utuh selalu mengalahkan pencocokan kata, tanpa koreksi huruf. */
    private fun cariNomorPart(catalog: Catalog, parsed: ParsedQuery): List<SearchHit>? {
        val nomor = TeksFoto(parsed.asli).nomorPart
        val kunci = (nomor + parsed.asli).map(::nomorMentah).toSet()
        val parts = catalog.parts.filter { part ->
            part.partNumber?.let { nomorMentah(it) in kunci } == true
        }
        if (parts.isNotEmpty()) return hitDari(catalog, parts, parsed)
        // Nomor tak dikenal tidak boleh jatuh ke fuzzy matching potongan angka.
        return if (nomor.isNotEmpty()) emptyList() else null
    }

    /**
     * Koreksi OCR hanya di jalur foto, hanya sesudah nomor persis tidak ketemu.
     * Hasil ambigu tidak dipilih. Konteks mobil/tahun dari foto tetap disaring.
     */
    fun dariFoto(catalog: Catalog, terbaca: TeksFoto): List<SearchHit> {
        if (terbaca.kosong) return emptyList()
        val parsed = cari(catalog, terbaca.teks)
        if (terbaca.nomorPart.isEmpty()) return hits(catalog, parsed)
        val mentah = terbaca.nomorPart.map(::nomorMentah).toSet()
        val persis = catalog.parts.filter { part ->
            part.partNumber?.let { nomorMentah(it) in mentah } == true
        }
        if (persis.isNotEmpty()) return hitDari(catalog, persis, parsed)
        val parts = terbaca.nomorPart.flatMap { nomor ->
            val koreksi = nomorKoreksi(nomor)
            catalog.parts.filter { part ->
                part.partNumber?.let { nomorKoreksi(it) == koreksi } == true
            }.takeIf { it.size == 1 }.orEmpty()
        }.distinctBy { it.id }
        return hitDari(catalog, parts, parsed).map { hit ->
            hit.copy(catatan = listOfNotNull(
                "Kandidat dari koreksi OCR. Periksa nomor pada barang: ${hit.part.partNumber}.",
                hit.catatan,
            ).joinToString(" "))
        }
    }

    private fun hitDari(catalog: Catalog, parts: List<Part>, parsed: ParsedQuery): List<SearchHit> {
        val hasil = parts.flatMap { part ->
            catalog.groupParts.filter { it.partId == part.id }.mapNotNull { link ->
                val grup = catalog.grup(link.groupId) ?: return@mapNotNull null
                val fitments = catalog.fitments.filter { f ->
                    f.groupId == grup.id && f.cocokTahun(parsed.tahun) &&
                        (parsed.mobil.isEmpty() || parsed.mobil.any { it.id == f.vehicleId })
                }
                if (parsed.mobil.isNotEmpty() && fitments.isEmpty()) return@mapNotNull null
                SearchHit(
                    part = part,
                    group = grup,
                    status = label(catalog, grup.id, part.id, fitments, parsed.adaMobil),
                    matches = fitments,
                    kendaraan = fitments.mapNotNull { catalog.kendaraan(it.vehicleId) }.distinctBy { it.id },
                    catatan = part.notes ?: grup.notes,
                    vehicleSpecific = parsed.adaMobil,
                )
            }
        }
        return HitOrdering.urutkan(hasil)
    }

    private fun nomorMentah(nomor: String): String =
        nomor.uppercase().filter { it.isLetterOrDigit() }

    private fun nomorKoreksi(nomor: String): String =
        nomorMentah(nomor).map { huruf ->
            when (huruf) {
                'O' -> '0'
                'I', 'L' -> '1'
                'S' -> '5'
                'B' -> '8'
                else -> huruf
            }
        }.joinToString("")

    /**
     * Label kartu:
     *  - ada kecocokan untuk mobil yang disebut -> status terendah dari data itu;
     *  - tidak menyebut mobil -> status yang tercatat pada barangnya sendiri;
     *  - tidak ada data sama sekali -> BELUM ADA DATA.
     */
    private fun label(
        catalog: Catalog,
        groupId: String,
        partId: String,
        fitments: List<Fitment>,
        mobilDisebut: Boolean,
    ): Status {
        val statusPart = catalog.statusPart(groupId, partId)
        return if (mobilDisebut) {
            Status.terendahDiketahui(fitments.map { it.status } + statusPart)
        } else {
            statusPart
        }
    }

    /** Pencarian bebas tanpa mobil: dipakai saat data untuk mobil itu belum ada. */
    private fun cariTeksBebas(catalog: Catalog, kata: List<String>): List<SearchHit> =
        catalog.groups.flatMap { grup ->
            val kategori = catalog.kategori(grup.categoryId)
            val teksGrup = listOfNotNull(grup.label, grup.notes, kategori?.nama).joinToString(" ")
            if (!TextMatch.semuaCocok(kata, teksGrup)) return@flatMap emptyList()
            grupKeHits(catalog, grup.id, emptyList(), mobilDisebut = false, kata)
        }

    private fun cocokTeks(catalog: Catalog, grup: tech.cocok.app.domain.model.FitmentGroup, parsed: ParsedQuery): Boolean {
        val kategori = catalog.kategori(grup.categoryId)
        val teks = buildString {
            append(grup.label).append(' ')
            append(grup.notes ?: "").append(' ')
            append(kategori?.nama ?: "").append(' ')
            catalog.partDiGrup(grup.id).forEach { p ->
                append(p.name).append(' ')
                append(p.partNumber ?: "").append(' ')
                append(p.brand ?: "").append(' ')
            }
        }
        return TextMatch.semuaCocok(parsed.kataBarang, teks)
    }

    private fun grupKeHits(
        catalog: Catalog,
        groupId: String,
        fitments: List<Fitment>,
        mobilDisebut: Boolean,
        kata: List<String>,
    ): List<SearchHit> {
        val grup = catalog.grup(groupId) ?: return emptyList()
        val kendaraan = fitments.mapNotNull { f ->
            catalog.kendaraan(f.vehicleId)?.let { it to f }
        }

        return catalog.partDiGrup(groupId)
            .filter { part -> kata.isEmpty() || cocokPart(part, kata) }
            .map { part ->
                SearchHit(
                    part = part,
                    group = grup,
                    status = label(catalog, groupId, part.id, fitments, mobilDisebut),
                    matches = fitments,
                    kendaraan = kendaraan.map { it.first }.distinctBy { it.id },
                    catatan = part.notes ?: grup.notes,
                    vehicleSpecific = mobilDisebut,
                )
            }
    }

    private fun cocokPart(part: Part, kata: List<String>): Boolean {
        val teks = listOfNotNull(part.name, part.partNumber, part.brand, part.notes).joinToString(" ")
        return TextMatch.semuaCocok(kata, teks)
    }

    /** Barang pengganti: semua part lain di grup yang sama (README §6.4). */
    fun pengganti(catalog: Catalog, part: Part): List<SearchHit> {
        val grup = catalog.groups.firstOrNull { g ->
            catalog.groupParts.any { it.groupId == g.id && it.partId == part.id }
        } ?: return emptyList()
        return HitOrdering.urutkan(grupKeHits(catalog, grup.id, emptyList(), mobilDisebut = false, emptyList()))
            .filter { it.part.id != part.id }
    }

    /** Kategori yang paling sering dipakai, untuk chip "Yang sering dicari". */
    fun kategoriSering(catalog: Catalog, batas: Int = 4): List<PartCategory> =
        catalog.categories
            .sortedByDescending { kat -> catalog.fitments.count { f -> catalog.grup(f.groupId)?.categoryId == kat.id } }
            .take(batas)

    fun kendaraanSering(catalog: Catalog, batas: Int = 4): List<Vehicle> =
        catalog.vehicles.sortedByDescending { v -> catalog.fitments.count { it.vehicleId == v.id } }.take(batas)
}
