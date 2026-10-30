package hu.szileemester.adamazon.domain

import java.text.Normalizer as JavaNormalizer
import java.util.Locale

object Normalizer {
    private val HU = Locale.forLanguageTag("hu-HU")
    private val WHITESPACE = Regex("\\s+")
    private val MARKS = Regex("\\p{Mn}+")

    /** Trimmed, single-spaced text with the first letter capitalised – used for display. */
    fun clean(text: String): String =
        text.trim().replace(WHITESPACE, " ").replaceFirstChar { it.titlecase(HU) }

    /** Lowercase, accent-free, hyphen-free form used for matching and as a lookup key. */
    fun key(text: String): String {
        val lower = text.trim().lowercase(HU).replace('-', ' ').replace(WHITESPACE, " ")
        return MARKS.replace(JavaNormalizer.normalize(lower, JavaNormalizer.Form.NFD), "")
    }

    /** Key that is safe to use as a Firestore document id. */
    fun docId(text: String): String = key(text).replace('/', '_').ifBlank { "_" }
}
