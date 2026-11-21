package tech.cocok.app.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.data.SeedData
import tech.cocok.app.domain.model.CandidateOrigin

/**
 * Editing a catalog item's specs must stay savable to that catalog row.
 *
 * Regression: `editItem` clears `catalogPartId` (so verification can't ride along),
 * which also hid the "Simpan spesifikasi" button and made `simpanSpesifikasi(null)`
 * a silent no-op. Filled-in specs were therefore lost on the next comparison.
 */
@RunWith(AndroidJUnit4::class)
class SpecEditPersistenceTest {

    private val app: Application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test
    fun editingSpecsKeepsTheCatalogRowSavable() = runBlocking {
        val model = MatchViewModel(app)
        val part = SeedData.pStopRebornAsli
        model.useCatalogItem(part.id, SeedData.catalog)
        assertEquals(part.id, model.state.value.source?.catalogPartId)

        model.editItem(part.id) { item ->
            item.copy(specifications = item.specifications + ("connector" to "H4"))
        }
        val edited = model.state.value.source!!

        // The edited item is no longer a trusted catalog binding (verification safety)...
        assertEquals(null, edited.catalogPartId)
        assertEquals(CandidateOrigin.INPUT, edited.origin)
        assertEquals("H4", edited.specifications["connector"])

        // ...but it must still remember which catalog row it came from, otherwise the
        // "save specs" action has no target and the edit is discarded.
        assertEquals(
            "edited item must keep the catalog row it belongs to",
            part.id,
            edited.linkedCatalogPartId,
        )
    }
}
