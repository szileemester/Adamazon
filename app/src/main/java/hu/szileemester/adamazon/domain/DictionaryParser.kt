package hu.szileemester.adamazon.domain

import org.json.JSONObject

/** Parses `assets/dictionary.json`: `{ "<categoryId>": { "<stem>": "<emoji>", ... }, ... }`. */
object DictionaryParser {
    fun parse(json: String): List<DictionaryEntry> {
        val root = JSONObject(json)
        return root.keys().asSequence().flatMap { categoryId ->
            val stems = root.getJSONObject(categoryId)
            stems.keys().asSequence().map { stem -> DictionaryEntry(stem, categoryId, stems.getString(stem)) }
        }.toList()
    }
}
