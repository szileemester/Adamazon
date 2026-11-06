package hu.szileemester.adamazon.domain

import org.json.JSONArray

/** One emoji of `assets/emoji_hu.json` (built by tools/emoji from Unicode CLDR). */
data class EmojiEntry(val emoji: String, val group: String, val name: String, val keywords: List<String>) {
    /** Accent-free name and keywords, for searching. */
    internal val searchText: String = Normalizer.key((listOf(name) + keywords).joinToString(" | "))
    internal val searchName: String = Normalizer.key(name)
}

/** Every emoji with its Hungarian name and keywords, searchable without accents. */
class EmojiCatalog(val entries: List<EmojiEntry>) {
    val groups: List<String> = entries.map { it.group }.distinct()

    fun inGroup(group: String): List<EmojiEntry> = entries.filter { it.group == group }

    /**
     * Emoji whose name or keywords contain every word of [query] ("tejfol", "kutya", "tiszt").
     * Names that start with the query come first, then other name matches, then keyword matches.
     */
    fun search(query: String): List<EmojiEntry> {
        val words = Normalizer.key(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries
            .filter { entry -> words.all { it in entry.searchText } }
            .sortedBy { entry ->
                when {
                    entry.searchName.startsWith(words.first()) -> 0
                    words.all { it in entry.searchName } -> 1
                    else -> 2
                }
            }
    }

    companion object {
        fun parse(json: String): EmojiCatalog {
            val array = JSONArray(json)
            return EmojiCatalog(
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    EmojiEntry(
                        emoji = o.getString("e"),
                        group = o.getString("g"),
                        name = o.getString("n"),
                        keywords = o.optString("k").split('|').filter { it.isNotEmpty() },
                    )
                },
            )
        }
    }
}
