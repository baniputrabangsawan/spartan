package tech.cocok.app.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.domain.model.InternetImage

/**
 * Guards the image-search plumbing without a network call: results must land in state
 * with a usable thumbnail, and picking one must yield a comparable candidate and a
 * source (otherwise the comparison screen has only one side).
 */
@RunWith(AndroidJUnit4::class)
class MatchViewModelInternetTest {

    private val app: Application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private val image = InternetImage(
        title = "Rear Light Tail Lamp FOR TOYOTA HILUX N120 815600K290 | eBay",
        thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSkT-VXyu0YXMMDEteKQIhv1l-MFyjWzKxU2s2qPuRMgw&s=10",
        imageUrl = "https://i.ebayimg.com/images/g/4rsAAOSwYSJnikym/s-l1200.jpg",
        sourceUrl = "https://www.ebay.com/itm/205229509900",
    )

    @Test
    fun choosingAnInternetResultProducesAComparableCandidateWithPhoto() = runBlocking {
        val model = MatchViewModel(app)
        model.editKeyword("stop hilux rebon 2018")
        model.setPhoto("/data/user/0/tech.cocok.app/cache/foto/x.jpg")

        val candidateId = model.useInternetCandidate(image)
        val state = model.state.value

        val candidate = state.candidates.single { it.item.id == candidateId }
        assertEquals(image.thumbnailUrl, candidate.item.photo)
        assertEquals(image.sourceUrl, candidate.item.sourceUrl)
        assertTrue("internet candidates are never verified", candidate.item.catalogPartId == null)

        // A photo/keyword search has no catalog source; one must be synthesised so the
        // comparison has two sides instead of dead-ending.
        val source = assertNotNull(state.source).let { state.source!! }
        assertEquals("/data/user/0/tech.cocok.app/cache/foto/x.jpg", source.photo)
        assertTrue(source.id != candidate.item.id)
    }

    @Test
    fun choosingTheSameInternetResultTwiceDoesNotDuplicateIt() = runBlocking {
        val model = MatchViewModel(app)
        val first = model.useInternetCandidate(image)
        val second = model.useInternetCandidate(image)
        assertEquals(first, second)
        assertEquals(1, model.state.value.candidates.count { it.item.id == first })
    }

    @Test
    fun keywordSearchAloneStillProvidesASourceSide() = runBlocking {
        val model = MatchViewModel(app)
        model.editKeyword("stop hilux rebon 2018")
        val stateAfterPick = model.state.value
        // No pick yet: source is empty until a search runs, which is expected.
        assertTrue(stateAfterPick.source == null)

        val catalog = tech.cocok.app.data.SeedData.catalog
        model.searchLocal(catalog, label = "stop hilux rebon 2018")
        // Source is set synchronously; candidates arrive from a coroutine, so wait for
        // the result rather than sampling too early.
        val deadline = System.currentTimeMillis() + 5_000
        while (model.state.value.candidates.isEmpty() && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(50)
        }
        val source = model.state.value.source
        assertNotNull("keyword search must create a source side", source)
        assertEquals("stop hilux rebon 2018", source!!.name)
        val n = model.state.value.candidates.size
        assertTrue("kandidat kosong", n > 0)
    }
}
