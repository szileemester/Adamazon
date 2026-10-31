package hu.szileemester.adamazon.domain

data class DictionaryEntry(val stem: String, val categoryId: String, val emoji: String)

data class CategoryGuess(val categoryId: String, val emoji: String, val source: Source) {
    enum class Source { LEARNED, DICTIONARY, FALLBACK }
}

/**
 * Guesses category and emoji for an item name.
 *
 * Learned mappings (manual corrections) win; otherwise the longest dictionary stem that starts
 * one of the words wins, which tolerates Hungarian suffixes ("paradicsomot") and compounds
 * ("fokhagymás"). Accents are folded, so lengthened stems ("almát") still match.
 */
class Categorizer(
    entries: List<DictionaryEntry>,
    private val fallbackCategoryId: String = FALLBACK_CATEGORY_ID,
) {
    private val normalized = entries.map { it.copy(stem = Normalizer.key(it.stem)) }
    private val singleWord = normalized.filter { ' ' !in it.stem }.sortedByDescending { it.stem.length }
    private val multiWord = normalized.filter { ' ' in it.stem }

    fun guess(
        name: String,
        learned: Map<String, LearnedMapping>,
        categoryEmoji: (String) -> String?,
    ): CategoryGuess {
        val key = Normalizer.key(name)
        learned[key]?.let { return CategoryGuess(it.categoryId, it.emoji, CategoryGuess.Source.LEARNED) }

        val entry = match(key)
        if (entry != null) {
            val emoji = entry.emoji.ifBlank { categoryEmoji(entry.categoryId).orEmpty() }
            return CategoryGuess(entry.categoryId, emoji, CategoryGuess.Source.DICTIONARY)
        }
        return CategoryGuess(
            fallbackCategoryId,
            categoryEmoji(fallbackCategoryId).orEmpty(),
            CategoryGuess.Source.FALLBACK,
        )
    }

    /**
     * Hungarian puts the head noun last ("fokhagymás kenyér" is bread), so the match that reaches
     * furthest to the right wins; among those the longest stem wins, which lets multi-word entries
     * ("mosogatógép só") beat their last word.
     */
    private fun match(key: String): DictionaryEntry? {
        var best: DictionaryEntry? = null
        var bestEnd = -1
        fun offer(entry: DictionaryEntry, end: Int) {
            val current = best
            if (current == null || end > bestEnd || (end == bestEnd && entry.stem.length > current.stem.length)) {
                best = entry
                bestEnd = end
            }
        }

        val padded = " $key"
        for (entry in multiWord) {
            val index = padded.lastIndexOf(" ${entry.stem}")
            if (index >= 0) offer(entry, wordEnd(key, index + entry.stem.length - 1))
        }
        var start = 0
        for (word in key.split(' ')) {
            singleWord.firstOrNull { matchesWord(word, it.stem) }?.let { offer(it, start + word.length) }
            start += word.length + 1
        }
        return best
    }

    /**
     * True for a word that names a product on its own ("tej", "almát"), false for adjectives made
     * from one ("almás", "fokhagymás") – used to tell where a dictated item ends.
     */
    fun isProductWord(word: String): Boolean {
        val key = Normalizer.key(word)
        val entry = singleWord.firstOrNull { matchesWord(key, it.stem) } ?: return false
        return !ADJECTIVE_SUFFIX.matches(key.substring(entry.stem.length))
    }

    /** True when two words are one product together: "csirke comb" (csirkecomb), "kovászos uborka". */
    fun isOneProduct(first: String, second: String): Boolean {
        val a = Normalizer.key(first)
        val b = Normalizer.key(second)
        val joined = a + b
        val spaced = "$a $b"
        return singleWord.any { it.stem.length > a.length && matchesWord(joined, it.stem) } ||
            multiWord.any { spaced.startsWith(it.stem) || it.stem.startsWith(spaced) }
    }

    private fun wordEnd(key: String, from: Int): Int =
        key.indexOf(' ', from).let { if (it < 0) key.length else it }

    private fun matchesWord(word: String, stem: String): Boolean =
        word.startsWith(stem) && (stem.length > SHORT_STEM || word.length - stem.length <= SHORT_STEM_SUFFIX)

    companion object {
        const val FALLBACK_CATEGORY_ID = "egyeb"
        private const val SHORT_STEM = 3
        private const val SHORT_STEM_SUFFIX = 3
        private val ADJECTIVE_SUFFIX = Regex("[aeiou]?s|i")
    }
}
