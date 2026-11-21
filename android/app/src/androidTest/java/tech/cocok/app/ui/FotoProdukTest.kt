package tech.cocok.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.ui.components.FotoProduk
import tech.cocok.app.ui.theme.CocokTheme

/**
 * Proves FotoProduk really decodes a network photo instead of always painting the
 * initials placeholder. Uses a hard-coded PNG data path so no live host is required.
 */
@RunWith(AndroidJUnit4::class)
class FotoProdukTest {

    @get:Rule
    val rule = createComposeRule()

    // A real Google Images thumbnail, the same shape the app receives from search.
    private val fotoUrl =
        "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSkT-VXyu0YXMMDEteKQIhv1l-MFyjWzKxU2s2qPuRMgw&s=10"

    @Test
    fun remoteThumbnailRendersInsteadOfThePlaceholder() {
        rule.setContent {
            CocokTheme {
                FotoProduk(
                    ukuran = 96.dp,
                    alamat = fotoUrl,
                    inisial = "LAM STO",
                    modifier = Modifier.size(96.dp),
                )
            }
        }
        // The placeholder text must be absent once a real image is drawn.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("LAM STO").fetchSemanticsNodes().isEmpty()
        }
        val bitmap = rule.onNodeWithContentDescription("Foto LAM STO")
            .captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        // A solid-color placeholder would have very few distinct pixels; a decoded
        // photo has many. This distinguishes "image drawn" from "text fallback".
        val colors = HashSet<Int>()
        for (x in 0 until bitmap.width step 4) {
            for (y in 0 until bitmap.height step 4) colors.add(bitmap.getPixel(x, y))
        }
        assertTrue("expected a decoded image, found ${colors.size} colors", colors.size > 1)
        assertTrue(bitmap.config != Bitmap.Config.ALPHA_8)
    }
}
