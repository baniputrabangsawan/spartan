package tech.cocok.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.search.QueryParser
import tech.cocok.app.domain.search.TextMatch
import tech.cocok.app.domain.usecase.CompareParts
import tech.cocok.app.domain.usecase.SearchParts

/**
 * Uji aturan yang mudah salah dan merugikan toko kalau keliru:
 * salah ketik, "belum ada data" bukan "beda", dan hanya pemilik yang
 * boleh menetapkan status.
 */
class PencarianTest {

    private val parser = QueryParser(SeedData.kendaraan, SeedData.kategori, SeedData.sinonim)

    @Test
    fun `salah ketik rebon dikenali sebagai reborn`() {
        val hasil = parser.parse("stop hilux rebon 2018")
        assertEquals(2018, hasil.tahun)
        assertEquals("Lampu stop belakang", hasil.kategori.map { it.nama }.first())
        assertTrue(hasil.mobil.any { it.generation == "Reborn" })
    }

    @Test
    fun `avansa dikenali sebagai avanza`() {
        val hasil = parser.parse("kampas rem avansa 2015")
        assertTrue(hasil.mobil.any { it.model == "Avanza" })
        assertEquals(2015, hasil.tahun)
    }

    @Test
    fun `generasi yang disebut harus ikut cocok`() {
        val vigo = parser.parse("stop hilux vigo")
        assertTrue(vigo.mobil.all { it.generation == "Vigo" })
        assertFalse(vigo.mobil.any { it.generation == "Reborn" })
    }

    @Test
    fun `tahun tanpa jenis barang tetap menghasilkan mobil`() {
        val hasil = parser.parse("hilux 2018")
        assertEquals(2018, hasil.tahun)
        assertTrue(hasil.mobil.isNotEmpty())
        assertTrue(hasil.kategori.isEmpty())
        assertFalse(hasil.kosong)
    }

    @Test
    fun `ketikan kosong tidak melempar`() {
        val hasil = parser.parse("   ")
        assertTrue(hasil.kosong)
    }

    @Test
    fun `jarak edit masuk akal`() {
        assertEquals(0, TextMatch.jarak("reborn", "reborn"))
        assertEquals(1, TextMatch.jarak("rebon", "reborn"))
        assertTrue(TextMatch.mirip("rebon", "reborn"))
        assertFalse(TextMatch.mirip("hilux", "avanza"))
    }

    @Test
    fun `status terendah menang saat menggabungkan`() {
        assertEquals(Status.TIDAK_COCOK, Status.terendah(listOf(Status.PASTI, Status.TIDAK_COCOK)))
        assertEquals(Status.MUNGKIN, Status.terendah(listOf(Status.PASTI, Status.MUNGKIN)))
        assertEquals(Status.BELUM_ADA_DATA, Status.terendah(emptyList()))
    }

    @Test
    fun `pencarian mengembalikan hasil berlabel`() {
        val pencarian = SearchParts()
        val hits = pencarian.hits(SeedData.catalog, parser.parse("stop hilux rebon 2018"))
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.status == Status.PASTI })
        // Pasti всегда выше mungkin.
        assertEquals(Status.PASTI, hits.first().status)
    }

    @Test
    fun `mencari mobil tertentu tidak menampilkan barang mobil lain`() {
        val pencarian = SearchParts()
        val hits = pencarian.hits(SeedData.catalog, parser.parse("stop hilux rebon 2018"))
        assertTrue(hits.isNotEmpty())
        // Lampu stop Vigo punya nomor part lain. Ia tidak boleh muncul saat yang
        // dicari Reborn, apalagi berlabel PASTI COCOK.
        assertTrue(hits.none { it.part.id == SeedData.pStopVigoAsli.id })
        // Dan semua hasil harus benar-benar punya kecocokan untuk Reborn 2018.
        hits.forEach { hit ->
            assertTrue(hit.matches.any { it.vehicleId == SeedData.reborn.id })
        }
    }

    @Test
    fun `pengganti menampilkan status yang tercatat bukan belum ada data`() {
        val pencarian = SearchParts()
        val pengganti = pencarian.pengganti(SeedData.catalog, SeedData.pStopRebornAsli)
        assertTrue(pengganti.isNotEmpty())
        // Grup tanpa fitment mobil bukan alasan menurunkan label barang yang sudah tercatat.
        assertEquals(Status.MUNGKIN, pengganti.first { it.part.id == SeedData.pStopRebornDepo.id }.status)
    }

    @Test
    fun `status yang diketahui tidak diturunkan oleh belum ada data`() {
        assertEquals(Status.MUNGKIN, Status.terendahDiketahui(listOf(Status.BELUM_ADA_DATA, Status.MUNGKIN)))
        assertEquals(Status.PASTI, Status.terendahDiketahui(listOf(Status.BELUM_ADA_DATA, Status.PASTI)))
        assertEquals(Status.BELUM_ADA_DATA, Status.terendahDiketahui(listOf(Status.BELUM_ADA_DATA)))
    }
}

class BandingkanTest {

    private val pembanding = CompareParts()

    @Test
    fun `kampas rem avanza antar generasi SAMA`() {
        val hasil = pembanding.bandingkan(
            SeedData.catalog, SeedData.kampasRem,
            SeedData.avanzaLama, 2013, SeedData.avanzaBaru, 2018,
        )
        assertEquals(tech.cocok.app.domain.model.CompareAnswer.SAMA, hasil.jawaban)
    }

    @Test
    fun `kaca depan vigo dan reborn BEDA karena keduanya pasti`() {
        val hasil = pembanding.bandingkan(
            SeedData.catalog, SeedData.kacaDepan,
            SeedData.vigo, 2010, SeedData.reborn, 2018,
        )
        assertEquals(tech.cocok.app.domain.model.CompareAnswer.BEDA, hasil.jawaban)
    }

    @Test
    fun `mobil tanpa data menghasilkan BELUM TAHU bukan BEDA`() {
        val hasil = pembanding.bandingkan(
            SeedData.catalog, SeedData.kacaDepan,
            SeedData.vigo, 2010, SeedData.hiluxBaru, 2024,
        )
        assertEquals(tech.cocok.app.domain.model.CompareAnswer.BELUM_TAHU, hasil.jawaban)
    }

    @Test
    fun `satu sisi tanpa data tidak pernah menghasilkan BEDA`() {
        val hasil = pembanding.bandingkan(
            SeedData.catalog, SeedData.filterOli,
            SeedData.vigo, 2010, SeedData.avanzaBaru, 2018,
        )
        assertTrue(hasil.jawaban != tech.cocok.app.domain.model.CompareAnswer.BEDA)
    }
}

class StatusHakAksesTest {

    @Test
    fun `kasir tidak bisa menetapkan pasti`() {
        val fitmentId = SeedData.fitments.first { it.status == Status.MUNGKIN }.id
        val setelah = SeedData.catalog.tetapkanStatus(
            fitmentId, Status.PASTI, tech.cocok.app.domain.model.Role.KASIR, "2026-11-18",
        )
        assertEquals(
            Status.MUNGKIN,
            setelah.fitments.first { it.id == fitmentId }.status,
        )
    }

    @Test
    fun `pemilik bisa menetapkan pasti`() {
        val fitmentId = SeedData.fitments.first { it.status == Status.MUNGKIN }.id
        val setelah = SeedData.catalog.tetapkanStatus(
            fitmentId, Status.PASTI, tech.cocok.app.domain.model.Role.PEMILIK, "2026-11-18",
        )
        assertEquals(Status.PASTI, setelah.fitments.first { it.id == fitmentId }.status)
    }

    @Test
    fun `belum ada data tidak pernah disimpan sebagai status`() {
        val fitmentId = SeedData.fitments.first().id
        val sebelum = SeedData.catalog
        val setelah = sebelum.tetapkanStatus(
            fitmentId, Status.BELUM_ADA_DATA, tech.cocok.app.domain.model.Role.PEMILIK, "2026-11-18",
        )
        assertEquals(sebelum, setelah)
    }

    @Test
    fun `konfirmasi kasir tidak menaikkan status ke pasti`() {
        val fitment = SeedData.fitments.first { it.status == Status.MUNGKIN }
        val setelah = SeedData.catalog.tambahKonfirmasi(
            tech.cocok.app.domain.model.Confirmation(
                id = "cf-test", fitmentId = fitment.id, groupId = fitment.groupId,
                oleh = "Kasir", role = tech.cocok.app.domain.model.Role.KASIR,
                hasil = true, waktu = "2026-11-18",
            ),
        )
        assertEquals(Status.MUNGKIN, setelah.fitments.first { it.id == fitment.id }.status)
        assertEquals(1, setelah.confirmations.size)
    }
}
