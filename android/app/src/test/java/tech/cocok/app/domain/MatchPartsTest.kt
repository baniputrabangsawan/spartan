package tech.cocok.app.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.CandidateOrigin
import tech.cocok.app.domain.model.ComparisonKind
import tech.cocok.app.domain.model.MatchContext
import tech.cocok.app.domain.model.MatchEvidence
import tech.cocok.app.domain.model.MatchKind
import tech.cocok.app.domain.model.MatchItem
import tech.cocok.app.domain.model.Role
import tech.cocok.app.domain.model.SpecificationState
import tech.cocok.app.domain.usecase.MatchParts

/**
 * Matching rules: discovery is a clue, and only scoped owner evidence can label a
 * direct replacement. Anything unknown must stay unknown rather than become a pass.
 */
class MatchPartsTest {

    private val parts = MatchParts()
    private val catalog = SeedData.catalog

    private fun item(name: String, spec: Map<String, String> = emptyMap(), id: String = name) =
        MatchItem(id = id, name = name, specifications = spec)

    @Test
    fun `two items without any specs are candidates, never replacements`() {
        val result = parts.compare(catalog, item("Lampu A"), item("Lampu B"), MatchContext())

        assertEquals(ComparisonKind.CANDIDATE, result.kind)
        assertFalse(result.canVerify)
        assertTrue(result.rows.all { it.state == SpecificationState.MISSING })
        assertTrue(result.checks.isNotEmpty())
    }

    @Test
    fun `matching specs alone never upgrade to a verified replacement`() {
        val spec = mapOf("connector" to "H4", "pin_count" to "3")
        val result = parts.compare(catalog, item("Lampu A", spec), item("Lampu B", spec), MatchContext())

        assertEquals(ComparisonKind.CANDIDATE, result.kind)
        assertFalse(result.canVerify)
        assertTrue(result.rows.any { it.key == "connector" && it.state == SpecificationState.SAME })
        assertTrue(result.rows.any { it.key == "thread" && it.state == SpecificationState.MISSING })
    }

    @Test
    fun `a differing spec is reported and blocks verification`() {
        val result = parts.compare(
            catalog,
            item("Lampu A", mapOf("pin_count" to "3")),
            item("Lampu B", mapOf("pin_count" to "4")),
            MatchContext(),
        )

        assertEquals(ComparisonKind.DIFFERENCES, result.kind)
        assertFalse(result.canVerify)
        val row = result.rows.single { it.key == "pin_count" }
        assertEquals(SpecificationState.DIFFERENT, row.state)
    }

    @Test
    fun `numeric spec comparison ignores unit formatting but not real differences`() {
        assertEquals(
            SpecificationState.SAME,
            rowState(mapOf("voltage_v" to "12 V"), mapOf("voltage_v" to "12.0")),
        )
        assertEquals(
            SpecificationState.DIFFERENT,
            rowState(mapOf("voltage_v" to "12"), mapOf("voltage_v" to "24")),
        )
    }

    @Test
    fun `unknown placeholder values are treated as missing`() {
        val result = parts.compare(
            catalog,
            item("A", mapOf("connector" to "tidak tahu")),
            item("B", mapOf("connector" to "H4")),
            MatchContext(),
        )
        assertEquals(SpecificationState.MISSING, result.rows.single { it.key == "connector" }.state)
    }

    @Test
    fun `owner evidence without vehicle context is refused`() {
        val evidence = evidence(MatchKind.DIRECT_REPLACEMENT, vehicleId = "")
        assertTrue(runCatching { parts.record(catalog, evidence) }.isFailure)
    }

    @Test
    fun `owner evidence with a year outside the vehicle range is refused`() {
        val evidence = evidence(
            MatchKind.DIRECT_REPLACEMENT,
            vehicleId = SeedData.reborn.id,
            year = 1990,
            variant = SeedData.reborn.attributes,
        )
        assertTrue(runCatching { parts.record(catalog, evidence) }.isFailure)
    }

    @Test
    fun `kasir cannot record verification`() {
        val evidence = evidence(
            MatchKind.DIRECT_REPLACEMENT,
            vehicleId = SeedData.reborn.id,
            year = 2018,
            variant = SeedData.reborn.attributes,
            role = Role.KASIR,
        )
        assertTrue(runCatching { parts.record(catalog, evidence) }.isFailure)
    }

    @Test
    fun `owner evidence with complete scope is stored and later survives comparison`() {
        val pertama = SeedData.pStopRebornAsli.id
        val kedua = SeedData.pStopRebornDepo.id
        val variant = SeedData.reborn.attributes
        val recorded = parts.record(
            catalog,
            evidence(
                MatchKind.DIRECT_REPLACEMENT,
                first = pertama,
                second = kedua,
                vehicleId = SeedData.reborn.id,
                year = 2018,
                variant = variant,
            ),
        )
        assertEquals(1, recorded.matchEvidence.size)

        val context = MatchContext(SeedData.reborn.id, 2018, variant)
        val source = parts.item(recorded.part(pertama)!!)
        val candidate = parts.item(recorded.part(kedua)!!)
        assertEquals(ComparisonKind.VERIFIED_REPLACEMENT, parts.compare(recorded, source, candidate, context).kind)
        // Same evidence must not leak to another vehicle.
        val other = MatchContext(SeedData.vigo.id, 2010, variant)
        assertEquals(ComparisonKind.CANDIDATE, parts.compare(recorded, source, candidate, other).kind)
    }

    @Test
    fun `edited or internet items cannot inherit catalog identity`() {
        val stored = parts.item(SeedData.pStopRebornAsli)
        assertEquals(ComparisonKind.SAME_ITEM, parts.compare(catalog, stored, stored, MatchContext()).kind)

        val edited = stored.copy(name = stored.name + " edit")
        val candidate = parts.item(SeedData.pStopRebornDepo)
        val result = parts.compare(catalog, edited, candidate, MatchContext())
        assertEquals(ComparisonKind.CANDIDATE, result.kind)
        assertFalse(result.canVerify)

        val internet = stored.copy(catalogPartId = null, origin = CandidateOrigin.INTERNET)
        assertEquals(ComparisonKind.CANDIDATE, parts.compare(catalog, internet, candidate, MatchContext()).kind)
    }

    @Test
    fun `discovery never returns the source itself and keeps group peers`() {
        val found = parts.discover(catalog, "stop", sourcePartId = SeedData.pStopRebornAsli.id)
        assertTrue(found.none { it.item.catalogPartId == SeedData.pStopRebornAsli.id })
        assertTrue(found.any { it.item.catalogPartId == SeedData.pStopRebornDepo.id })
        assertTrue(found.all { it.reason.isNotBlank() })
    }

    @Test
    fun `keyword discovery without matches returns nothing rather than a guess`() {
        assertTrue(parts.discover(catalog, "zzzz tidak ada").isEmpty())
    }

    @Test
    fun `internet candidate with no identity stays unverifiable even when title matches`() {
        // Internet results arrive as MatchItem with a source URL but no catalog row.
        val web = MatchItem(
            id = "web-1",
            name = SeedData.pStopRebornAsli.name,
            partNumber = SeedData.pStopRebornAsli.partNumber,
            sourceUrl = "https://www.ebay.com/itm/1",
            origin = CandidateOrigin.INTERNET,
        )
        val source = parts.item(SeedData.pStopRebornAsli)
        val result = parts.compare(catalog, source, web, MatchContext())

        assertEquals(ComparisonKind.CANDIDATE, result.kind)
        assertFalse(result.canVerify)
        val askToSave = result.checks.any { it.contains("Simpan kedua identitas", ignoreCase = true) }
        assertTrue("an unbound candidate must ask to be saved before verification", askToSave)
    }

    @Test
    fun `filling a missing spec changes the row state but never grants verification`() {
        val empty = parts.compare(catalog, item("A"), item("B"), MatchContext())
        assertTrue(empty.rows.all { it.state == SpecificationState.MISSING })

        val filled = parts.compare(
            catalog,
            item("A", mapOf("connector" to "H4")),
            item("B", mapOf("connector" to "H4")),
            MatchContext(),
        )
        assertEquals(SpecificationState.SAME, filled.rows.single { it.key == "connector" }.state)
        assertEquals(ComparisonKind.CANDIDATE, filled.kind)
        assertFalse(filled.canVerify)
    }

    private fun rowState(
        first: Map<String, String>,
        second: Map<String, String>,
        key: String = "voltage_v",
    ): SpecificationState =
        parts.compare(catalog, item("A", first), item("B", second), MatchContext())
            .rows.single { it.key == key }.state

    private fun evidence(
        kind: MatchKind,
        first: String = SeedData.pStopRebornAsli.id,
        second: String = SeedData.pStopRebornDepo.id,
        vehicleId: String,
        year: Int = 2018,
        variant: Map<String, String> = emptyMap(),
        role: Role = Role.PEMILIK,
    ) = MatchEvidence(
        id = "e-1",
        firstPartId = first,
        secondPartId = second,
        vehicleId = vehicleId,
        year = year,
        variant = variant,
        kind = kind,
        note = "Diperiksa terpasang di toko.",
        by = "pemilik",
        role = role,
        createdAt = Instant.now().toString(),
    )
}
