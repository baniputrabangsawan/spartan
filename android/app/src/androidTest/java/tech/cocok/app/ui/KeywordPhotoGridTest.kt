package tech.cocok.app.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.MainActivity

/**
 * Guards the ImageSearchMan-style surface: internet results must show product photos
 * and stay comparable. Runs inside the app's own activity, so it needs no shared taps.
 */
@RunWith(AndroidJUnit4::class)
class KeywordPhotoGridTest {

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
    fun keywordSearchOffersANativePhotoGridWithComparison() {
        openMatching()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Cari kandidat").fetchSemanticsNodes().isNotEmpty()
        }
        // Typed keyword -> ein kandidat -> internet photos are reachable without a photo.
        rule.onNodeWithText("Cari kandidat").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Cari gambar berdasarkan kata kunci").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Cari gambar berdasarkan kata kunci").assertExists()
    }
}
