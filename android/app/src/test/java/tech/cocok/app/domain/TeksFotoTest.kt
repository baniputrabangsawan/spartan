package tech.cocok.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.model.TeksFoto
import tech.cocok.app.domain.usecase.SearchParts

/**
 * Teks di sini meniru hasil pembacaan foto (OCR), bukan teks yang diketik
 * pengguna: huruf besar, baris terpisah, dan sering ada spasi aneh.
 */
class TeksFotoTest {

    @Test
    fun `nomor part pada kemasan terbaca dan dipakai mencari`() {
        val foto = TeksFoto(
            """
            TOYOTA GENUINE PARTS
            81560-0K290
            LAMP ASSY, STOP LAMP
            """.trimIndent(),
        )
        assertEquals(listOf("81560-0K290"), foto.nomorPart)

        val hits = SearchParts().hits(SeedData.catalog, SearchParts().cari(SeedData.catalog, foto.nomorPart.first()))
        assertTrue(hits.isNotEmpty())
        assertEquals(SeedData.pStopRebornAsli.id, hits.first().part.id)
    }

    @Test
    fun `tahun pada label ikut terbaca`() {
        val foto = TeksFoto("HILUX 2018\n81560-0K290")
        assertEquals(2018, foto.tahun)
    }

    @Test
    fun `tidak ada nomor part berarti hasil kosong bukan tebakan`() {
        val foto = TeksFoto("LAMPU STOP")
        assertTrue(foto.nomorPart.isEmpty())
        assertNull(foto.tahun)
    }

    @Test
    fun `nomor part tak dikenal tidak menghasilkan kecocokan`() {
        val hits = SearchParts().hits(
            SeedData.catalog,
            SearchParts().cari(SeedData.catalog, "99999-ZZ999"),
        )
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `foto tidak pernah menaikkan keyakinan`() {
        // Barang merek lain hanya berstatus MUNGKIN; membaca nomornya di foto
        // tidak boleh membuatnya menjadi PASTI.
        val hits = SearchParts().hits(
            SeedData.catalog,
            SearchParts().cari(SeedData.catalog, "DEP-312-19A6L"),
        )
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.status != Status.PASTI })
    }

    @Test
    fun `OCR yang membaca O sebagai nol tetap menemukan nomor yang sama`() {
        // Terbukti di HP: kamera membaca "81560-0K290" menjadi "81560-OK290".
        val hits = SearchParts().dariFoto(SeedData.catalog, TeksFoto("81560-OK290"))
        assertEquals(listOf(SeedData.pStopRebornAsli.id), hits.map { it.part.id })
        assertTrue(hits.single().catatan.orEmpty().contains("koreksi OCR"))
    }

    @Test
    fun `koreksi huruf tidak dipakai bila hasilnya lebih dari satu barang`() {
        val dasar = SeedData.catalog
        val asli = SeedData.pStopRebornAsli
        val kedua = asli.copy(id = "p-kembar", partNumber = "81560-OK290")
        val link = dasar.groupParts.first { it.partId == asli.id }
        val katalog = dasar.copy(parts = dasar.parts + kedua, groupParts = dasar.groupParts + link.copy(partId = kedua.id))
        val pencarian = SearchParts()
        assertTrue(pencarian.dariFoto(katalog, TeksFoto("8I560-0K290")).isEmpty())
        assertEquals(listOf(kedua.id), pencarian.dariFoto(katalog, TeksFoto("81560-OK290")).map { it.part.id })
    }

    @Test
    fun `pencarian biasa tidak mengubah nomor dengan koreksi OCR`() {
        val pencarian = SearchParts()
        assertTrue(pencarian.hits(SeedData.catalog, pencarian.cari(SeedData.catalog, "81560-OK290")).isEmpty())
    }

    @Test
    fun `nomor part tetap mengikuti mobil dan tahun yang diminta`() {
        val pencarian = SearchParts()
        val hits = pencarian.hits(
            SeedData.catalog, pencarian.cari(SeedData.catalog, "81560-0K290 hilux reborn 2018"),
        )
        assertEquals(listOf(SeedData.pStopRebornAsli.id), hits.map { it.part.id })
        assertEquals(Status.PASTI, hits.single().status)
        assertTrue(hits.single().matches.all { it.vehicleId == SeedData.reborn.id })
        assertTrue(pencarian.hits(
            SeedData.catalog, pencarian.cari(SeedData.catalog, "81560-0K290 avanza 2018"),
        ).isEmpty())
    }

    @Test
    fun `foto tidak mencampur kecocokan dari mobil lain`() {
        val hits = SearchParts().dariFoto(SeedData.catalog, TeksFoto("81560-0K290\nHILUX VIGO 2010"))
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.status == Status.TIDAK_COCOK })
        assertTrue(hits.flatMap { it.matches }.all { it.vehicleId == SeedData.vigo.id })
    }

    @Test
    fun `beberapa nomor yang terbaca tidak berhenti di kecocokan pertama`() {
        val hits = SearchParts().dariFoto(SeedData.catalog, TeksFoto("81560-0K290\nDEP-312-19A6L"))
        assertEquals(
            setOf(SeedData.pStopRebornAsli.id, SeedData.pStopRebornDepo.id),
            hits.map { it.part.id }.toSet(),
        )
    }

    @Test
    fun `detail foto tidak berubah menjadi tidak cocok karena fitment mobil lain`() {
        val katalog = SeedData.catalog
        val hit = SearchParts().dariFoto(katalog, TeksFoto("81560-0K290")).single()
        assertEquals(Status.PASTI, hit.status)
        assertEquals(hit.status, hit.perbarui(katalog).status)
        val tanpaFitment = katalog.copy(fitments = emptyList())
        val pencarian = SearchParts()
        val spesifik = pencarian.hits(katalog, pencarian.cari(katalog, "81560-0K290 hilux reborn 2018")).single()
        assertEquals(Status.BELUM_ADA_DATA, spesifik.perbarui(tanpaFitment).status)
    }
}
