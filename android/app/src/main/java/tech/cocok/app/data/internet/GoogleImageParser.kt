package tech.cocok.app.data.internet

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tech.cocok.app.domain.model.InternetImage

/** Reads data literals, never evaluates downloaded JavaScript. Google may change this format. */
internal object GoogleImageParser {
    private val mapStart = Regex("\\bvar\\s+m\\s*=\\s*\\{")

    fun parse(script: String): List<InternetImage> {
        require(script.length <= 4 * 1024 * 1024) { "Data gambar terlalu besar." }
        val images = LinkedHashMap<String, InternetImage>()
        var recognized = false
        for (match in mapStart.findAll(script)) {
            val start = match.range.last
            val end = objectEnd(script, start) ?: continue
            val map = try {
                Json.parseToJsonElement(script.substring(start, end)) as? JsonObject
            } catch (_: IllegalArgumentException) { null } ?: continue
            recognized = true
            for (value in map.values) {
                val record = value as? JsonArray ?: continue
                if (record.getOrNull(0).text() != "1") continue
                val data = record.getOrNull(1) as? JsonArray ?: continue
                val thumbnail = (data.getOrNull(2) as? JsonArray)?.getOrNull(0).text() ?: continue
                val original = (data.getOrNull(3) as? JsonArray)?.getOrNull(0).text() ?: continue
                val metadata = data.getOrNull(9) as? JsonObject ?: continue
                val source = metadata["2003"] as? JsonArray ?: continue
                val url = source.getOrNull(2).text() ?: continue
                val title = source.getOrNull(3).text()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                if (!safeUrl(thumbnail) || !safeUrl(original) || !safeUrl(url)) continue
                images.putIfAbsent("$original\n$url", InternetImage(title, thumbnail, original, url))
                if (images.size == 100) return images.values.toList()
            }
        }
        if (!recognized) throw GoogleKeywordSearchException(
            GoogleKeywordSearchException.Reason.FORMAT,
            "Format hasil gambar Google berubah atau belum dapat dibaca.",
        )
        return images.values.toList()
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.content

    private fun safeUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in setOf(-1, 443)
    } catch (_: Exception) { false }

    private fun objectEnd(text: String, start: Int): Int? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return index + 1
            }
        }
        return null
    }
}
