package tech.cocok.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.MainActivity

/**
 * End-to-end keyword flow: typed text -> candidates -> comparison with both sides.
 * This is the path that previously dead-ended with "Pilih ulang".
 */
@RunWith(AndroidJUnit4::class)
class KeywordCompareFlowTest {

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
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Cari kandidat").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun keywordOnlySearchReachesARealComparison() {
        openMatching()
        rule.onNodeWithText("Nama barang, nomor part, atau mobil").performTextInput("stop hilux rebon 2018")
        rule.onNodeWithText("Cari kandidat").performClick()

        // Candidates must appear, then a pick must produce a comparison instead of
        // the "Kandidat tidak lagi tersedia. Pilih ulang." dead end.
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Kandidat dari data toko").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Bandingkan")[0].performScrollTo().performClick()

        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Spesifikasi (ketuk untuk mengisi)").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Spesifikasi (ketuk untuk mengisi)").assertIsDisplayed()
        rule.onAllNodesWithText("Kandidat tidak lagi tersedia. Pilih ulang.").assertCountEquals(0)
        // Both sides of the comparison must be present.
        rule.onAllNodesWithText("Sumber").assertCountEquals(1)
        rule.onAllNodesWithText("Kandidat").assertCountEquals(1)
    }
}
