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
import tech.cocok.app.domain.usecase.SearchParts

class PathCostTest {
    private fun synthetic(parts: Int): Catalog {
        val b = SeedData.catalog
        val ps = ArrayList<Part>(parts); val gs = ArrayList<FitmentGroup>(parts)
        val gp = ArrayList<GroupPart>(parts); val ft = ArrayList<Fitment>(parts)
        repeat(parts) { i ->
            val c = b.categories[i % b.categories.size]
            ps += Part("p-$i", c.id, "${c.name} $i", partNumber = "9%05d".format(i))
            gs += FitmentGroup("g-$i", c.id, "${c.name} g$i")
            gp += GroupPart("g-$i", "p-$i", Status.MUNGKIN)
            val v = b.vehicles[i % b.vehicles.size]
            ft += Fitment("f-$i", "g-$i", v.id, v.yearFrom, v.yearTo, status = Status.MUNGKIN)
        }
        return b.copy(parts = b.parts + ps, groups = b.groups + gs, groupParts = b.groupParts + gp, fitments = b.fitments + ft)
    }
    @Test fun substitutesAreFastOnATwentyThousandItemCatalog() {
        val cat = synthetic(20_000); val s = SearchParts()
        val part = cat.parts.last()
        repeat(2) { s.pengganti(cat, part) }
        val t0 = System.nanoTime(); val out = s.pengganti(cat, part)
        val ms = (System.nanoTime()-t0)/1_000_000
        assertTrue("substitute list was ${ms}ms; expected well under 150ms", ms < 150)
    }
}
