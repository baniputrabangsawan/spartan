package tech.cocok.app.data.internet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleImageParserTest {
    private fun fixture(): String = requireNotNull(javaClass.getResource("/google-image-three-results.js")).readText()

    @Test fun `real results retain image title and source pairing`() {
        val images = GoogleImageParser.parse(fixture())
        assertEquals(3, images.size)
        val first = images.first()
        assertEquals("https://www.ebay.com/itm/205229509900", first.sourceUrl)
        assertEquals("https://i.ebayimg.com/images/g/4rsAAOSwYSJnikym/s-l1200.jpg", first.imageUrl)
        assertTrue(first.title.contains("815600K290"))
        assertTrue(first.thumbnailUrl.startsWith("https://encrypted-tbn0.gstatic.com/"))
        assertEquals("https://www.ebay.co.uk/itm/393637668233", images[1].sourceUrl)
    }

    @Test fun `duplicate records do not duplicate cards`() {
        assertEquals(3, GoogleImageParser.parse(fixture() + fixture()).size)
    }

    @Test fun `unsafe source links are not offered to buyers`() {
        val changed = fixture().replace("https://www.ebay.com/itm/205229509900", "javascript:alert(1)")
        val images = GoogleImageParser.parse(changed)
        assertEquals(2, images.size)
        assertTrue(images.none { it.sourceUrl.startsWith("javascript:") })
    }

    @Test fun `braces and escaped quotes in titles do not truncate records`() {
        val changed = fixture().replace("Rear Light Tail Lamp", "Lamp {left} \\\"quoted\\\"")
        val images = GoogleImageParser.parse(changed)
        assertEquals(3, images.size)
        assertTrue(images.first().title.contains("{left} \"quoted\""))
    }

    @Test(expected = GoogleKeywordSearchException::class)
    fun `unknown page format is not reported as no results`() {
        GoogleImageParser.parse("<html>Google changed the format</html>")
    }

    @Test fun `known empty result map returns no images`() {
        assertTrue(GoogleImageParser.parse("var m={};var a=m;if(window.W_jd){}").isEmpty())
    }
}
