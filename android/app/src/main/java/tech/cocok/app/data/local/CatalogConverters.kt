package tech.cocok.app.data.local

import androidx.room.TypeConverter
import org.json.JSONObject

class CatalogConverters {
    @TypeConverter
    fun encodeMap(value: Map<String, String>): String {
        val json = JSONObject()
        for ((key, item) in value) json.put(key, item)
        return json.toString()
    }

    @TypeConverter
    fun decodeMap(value: String): Map<String, String> {
        val json = JSONObject(value)
        return buildMap(json.length()) {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = json.get(key)
                require(item is String) { "Catalog map value for '$key' must be a string" }
                put(key, item)
            }
        }
    }
}
