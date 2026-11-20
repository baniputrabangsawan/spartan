package tech.cocok.app.data.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.domain.model.Part

/**
 * Proves the on-device embedder really runs and returns comparable scores, instead
 * of the app merely claiming photo-shape matching. No UI, no shared-screen input.
 */
@RunWith(AndroidJUnit4::class)
class LocalVisualMatcherTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun part(id: String, photo: File) =
        Part(id = id, categoryId = "c-1", name = id, photoKey = photo.absolutePath)

    /** Distinct, non-trivial patterns so similarity is not driven by a blank canvas. */
    private fun pattern(seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, 224f, 224f, paint)
        paint.color = Color.rgb(20 + seed % 40, 90, 160)
        repeat(6) { i ->
            val y = 12f + i * 34f
            canvas.drawRect((seed * 7 % 60).toFloat(), y, 190f, y + 18f, paint)
        }
        if (seed % 2 == 0) {
            paint.color = Color.rgb(220, 60, 40)
            canvas.drawCircle(112f, 190f, 28f, paint)
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String): File =
        File(context.cacheDir, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

    @Test
    fun identicalPhotoScoresHigherThanDifferentPhoto() = runBlocking {
        val target = save(pattern(1), "visual-target.png")
        val identical = save(pattern(1), "visual-identical.png")
        val different = save(pattern(8), "visual-different.png")

        val scores = LocalVisualMatcher(context).search(
            Uri.fromFile(target),
            listOf(part("same", identical), part("other", different)),
        )

        assertEquals(setOf("same", "other"), scores.keys)
        assertTrue("finite scores", scores.values.all { it.isFinite() })
        assertTrue("within cosine range", scores.values.all { it in -1f..1f })
        assertTrue(
            "identical=${scores["same"]} different=${scores["other"]}",
            scores.getValue("same") > scores.getValue("other"),
        )
        assertTrue("identical should be near 1", scores.getValue("same") > 0.9f)
    }

    @Test
    fun catalogWithoutPhotosReturnsEmptyIndexInsteadOfGuessing() = runBlocking {
        val target = save(pattern(3), "visual-nophoto.png")
        val scores = LocalVisualMatcher(context).search(
            Uri.fromFile(target),
            listOf(Part(id = "no-photo", categoryId = "c-1", name = "Tanpa foto")),
        )
        assertTrue("must report no index, not a score", scores.isEmpty())
    }

    @Test
    fun internetUrlIsRejectedAsALocalPhoto() = runBlocking {
        val result = runCatching {
            LocalVisualMatcher(context).search(
                Uri.parse("https://example.com/foto.jpg"),
                listOf(part("p", save(pattern(2), "visual-remote-part.png"))),
            )
        }
        assertTrue("remote source must be refused", result.isFailure)
    }
}
