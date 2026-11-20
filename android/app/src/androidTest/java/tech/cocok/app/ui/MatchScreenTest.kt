package tech.cocok.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.MainActivity

/**
 * Matching flow on the real Compose surface. Runs in the app's own activity, so it
 * needs no shared-device taps and does not disturb other agents' screens.
 */
@RunWith(AndroidJUnit4::class)
class MatchScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openMatching() {
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("BUKA DATA LOKAL").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("BUKA DATA LOKAL").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Cari padanan barang").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Cari padanan barang").performClick()
    }

    @Test
    fun matchingScreenOffersSourcePhotoKeywordAndStoreStart() {
        openMatching()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Cari padanan barang").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Cari padanan barang").assertIsDisplayed()
        rule.onNodeWithText("Ambil foto barang").assertIsDisplayed()
        rule.onNodeWithText("Pilih dari galeri").assertIsDisplayed()
        rule.onNodeWithText("Cari kandidat").assertIsDisplayed()
        rule.onNodeWithText("Atau mulai dari data toko").assertIsDisplayed()
    }

    @Test
    fun comparisonShowsMissingSpecificationChecksInsteadOfAPass() {
        openMatching()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Lampu stop kiri asli").fetchSemanticsNodes().isNotEmpty()
        }
        // Start from a stored catalog item, then compare it with its group peer.
        rule.onAllNodesWithText("Lampu stop kiri asli")[0].performClick()
        rule.onNodeWithText("Bandingkan", useUnmergedTree = false).performClick()

        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Spesifikasi (ketuk untuk mengisi)").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Spesifikasi (ketuk untuk mengisi)").performScrollTo()
        rule.onNodeWithText("Yang masih perlu diperiksa").performScrollTo()
        // Unknown data must surface as physical check rows plus editable blanks, never as "sama".
        rule.onNodeWithText("Yang masih perlu diperiksa").assertIsDisplayed()
        rule.onAllNodesWithText("?", substring = true, useUnmergedTree = true)
            .assertCountEquals(7)
        rule.onAllNodesWithText("sama", substring = false, useUnmergedTree = true).assertCountEquals(0)
        // Blank specs must be fillable in place, otherwise the comparison can never improve.
        rule.onAllNodesWithText("belum ada", useUnmergedTree = true).assertCountEquals(14)
    }

    @Test
    fun kasirCannotRecordVerification() {
        openMatching()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Lampu stop kiri asli").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Lampu stop kiri asli")[0].performClick()
        rule.onNodeWithText("Bandingkan", useUnmergedTree = false).performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Konteks mobil untuk verifikasi").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Konteks mobil untuk verifikasi").performScrollTo()
        rule.onNodeWithText(
            "Hanya pemilik yang dapat mencatat verifikasi padanan. Kasir tetap bisa membandingkan dan menambah data.",
        ).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Catat pengganti langsung").assertCountEquals(0)
    }
}
