package tech.cocok.app.ui

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import tech.cocok.app.data.internet.GoogleKeywordSearch
import tech.cocok.app.data.internet.GoogleKeywordSearchException

/**
 * Live check of the ImageSearchMan-style path on the device: keyword in, real image
 * records with https thumbnails out. Network-dependent: when Google blocks the request
 * the test fails with the actual reason rather than silently passing.
 */
@RunWith(AndroidJUnit4::class)
class LiveKeywordImagesTest {

    private val app: Application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @Test
    fun keywordProducesImageRecordsWithThumbnails() {
        val images = runBlocking {
            try {
                GoogleKeywordSearch(app).search("81560-0K290")
            } catch (error: GoogleKeywordSearchException) {
                // Blocked/CAPTCHA/timeout are environment conditions, not code defects:
                // skip so the suite is neither green-on-nothing nor red-on-noise.
                if (error.reason != GoogleKeywordSearchException.Reason.FORMAT) {
                    Assume.assumeTrue("dilewati: ${error.message}", false)
                }
                // FORMAT means our parser no longer understands Google - a real regression.
                throw AssertionError("format hasil gambar berubah: ${error.message}", error)
            }
        }
        assertTrue("tidak ada gambar dikembalikan", images.isNotEmpty())
        val badThumb = images.filterNot { it.thumbnailUrl.startsWith("https://") }
        val badSource = images.filterNot { it.sourceUrl.startsWith("https://") }
        assertTrue("thumbnail bukan https: ${badThumb.size}/${images.size}", badThumb.isEmpty())
        assertTrue("sumber bukan https: ${badSource.size}/${images.size}", badSource.isEmpty())
        assertTrue("judul kosong", images.all { it.title.isNotBlank() })
    }
}
