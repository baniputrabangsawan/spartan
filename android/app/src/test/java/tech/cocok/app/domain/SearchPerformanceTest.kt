package tech.cocok.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.Catalog
import tech.cocok.app.domain.model.Fitment
import tech.cocok.app.domain.model.FitmentGroup
import tech.cocok.app.domain.model.GroupPart
import tech.cocok.app.domain.model.Part
import tech.cocok.app.domain.model.Status
import tech.cocok.app.domain.usecase.CompareParts
import tech.cocok.app.domain.usecase.SearchParts

/**
 * Guard against the O(groups x fitments) scan in SearchParts.hits: with a shop-sized
 * catalog this used to take ~1.6 s per keystroke, on the caller's thread.
 *
 * Budget is deliberately generous (a cheap CI device) - it fails on an accidental
 * quadratic scan, not on a slow machine.
 */
class SearchPerformanceTest {

    private fun synthetic(parts: Int): Catalog {
        val base = SeedData.catalog
        val categories = base.categories
        val vehicles = base.vehicles
        val extraParts = ArrayList<Part>(parts)
        val extraGroups = ArrayList<FitmentGroup>(parts)
        val extraGroupParts = ArrayList<GroupPart>(parts)
        val extraFitments = ArrayList<Fitment>(parts)
        repeat(parts) { i ->
            val category = categories[i % categories.size]
            val partId = "p-$i"
            val groupId = "g-$i"
            extraParts += Part(partId, category.id, "${category.name} contoh $i", partNumber = "9%05d".format(i))
            extraGroups += FitmentGroup(groupId, category.id, "${category.name} kelompok $i")
            extraGroupParts += GroupPart(groupId, partId, Status.MUNGKIN)
            val vehicle = vehicles[i % vehicles.size]
            extraFitments += Fitment("f-$i", groupId, vehicle.id, vehicle.yearFrom, vehicle.yearTo, status = Status.MUNGKIN)
        }
        return base.copy(
            parts = base.parts + extraParts,
            groups = base.groups + extraGroups,
            groupParts = base.groupParts + extraGroupParts,
            fitments = base.fitments + extraFitments,
        )
    }

    @Test
    fun searchStaysFastOnATwentyThousandItemCatalog() {
        val catalog = synthetic(20_000)
        val search = SearchParts()
        val query = search.cari(catalog, "stop hilux rebon 2018")
        repeat(3) { search.hits(catalog, query) } // warm up

        val times = LongArray(5)
        var found = 0
        for (i in times.indices) {
            val t0 = System.nanoTime()
            found = search.hits(catalog, query).size
            times[i] = System.nanoTime() - t0
        }
        times.sort()
        val p50Ms = times[times.size / 2] / 1_000_000
        assertTrue("search must still return results", found > 0)
        assertTrue("p50 search was ${p50Ms}ms on 20k items; expected well under 250ms", p50Ms < 250)
    }

    @Test
    fun compareStaysFastOnATwentyThousandItemCatalog() {
        val catalog = synthetic(20_000)
        val compare = CompareParts()
        repeat(2) { compare.bandingkan(catalog, SeedData.lampuStop, SeedData.vigo, 2010, SeedData.reborn, 2018) }

        val t0 = System.nanoTime()
        compare.bandingkan(catalog, SeedData.lampuStop, SeedData.vigo, 2010, SeedData.reborn, 2018)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("compare took ${ms}ms on 20k items; expected well under 150ms", ms < 150)
    }
}
