package hu.szileemester.adamazon.data

import android.content.Context
import org.json.JSONArray

data class StorePreset(val name: String, val domain: String, val colorArgb: Long)

object StorePresets {
    fun load(context: Context): List<StorePreset> {
        val array = JSONArray(context.assets.open("store_presets.json").bufferedReader().use { it.readText() })
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StorePreset(
                name = o.getString("name"),
                domain = o.getString("domain"),
                colorArgb = o.getString("color").removePrefix("#").toLong(16),
            )
        }
    }
}
