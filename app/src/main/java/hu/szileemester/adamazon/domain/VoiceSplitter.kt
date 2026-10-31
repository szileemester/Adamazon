package hu.szileemester.adamazon.domain

import java.util.Locale

/**
 * Turns dictated text into quick-add lines. Speech comes without punctuation ("két kiló alma tej
 * meg kenyér"), so an item ends at:
 * - a pause (each recognizer result is one utterance),
 * - a joining word ("és", "meg", "aztán"…) or punctuation, if the recognizer added any,
 * - a number, which starts the next item's quantity ("… alma három liter tej"); a quantity said
 *   on its own ("két kiló" … "alma") goes with the next item,
 * - a second product word right after one ("tej kenyér"), unless the two form one product
 *   ("csirke comb") – adjectives such as "fokhagymás" are not product words.
 * Number words become digits so the quantity parser understands them; fillers ("hát") are dropped.
 */
class VoiceSplitter(
    private val isProductWord: (String) -> Boolean,
    private val isOneProduct: (String, String) -> Boolean,
) {
    fun split(utterances: List<String>): List<String> {
        val result = mutableListOf<List<String>>()
        var quantity = emptyList<String>()
        for (chunk in utterances.flatMap(::splitOne)) {
            if (isBareQuantity(chunk)) {
                quantity += chunk
            } else {
                result += quantity + chunk
                quantity = emptyList()
            }
        }
        // A quantity said last belongs to the item before it.
        when {
            quantity.isEmpty() -> Unit
            result.isEmpty() -> result += quantity
            else -> result[result.lastIndex] = result.last() + quantity
        }
        return result.map { it.joinToString(" ") }
    }

    private fun splitOne(utterance: String): List<List<String>> {
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        fun close() {
            if (current.isNotEmpty()) chunks += current
            current = mutableListOf()
        }

        val words = tokens(utterance)
        words.forEachIndexed { index, word ->
            if (word == BREAK) return@forEachIndexed close()
            val key = Normalizer.key(word)
            if (key in JOINERS) return@forEachIndexed close()
            // Accents matter here: "hat" is six, "hát" a filler.
            val lower = word.lowercase(HU)
            if (lower in FILLERS) return@forEachIndexed

            val next = words.getOrNull(index + 1)
            val number = numberOf(lower, next)
            when {
                // "egy tej": an article, not a quantity.
                number == null && key == "egy" -> close()
                number != null || NUMBER.matches(word) -> {
                    close()
                    current += number ?: word
                }
                else -> {
                    val last = current.lastOrNull()
                    val productAfterProduct = last != null && !QuantityParser.isUnit(last) &&
                        isProductWord(last) && isProductWord(word) && !isOneProduct(last, word)
                    if (productAfterProduct) close()
                    current += word
                }
            }
        }
        close()
        return mergeTrailingQuantities(chunks)
    }

    /** Within one breath, "tej" + "2 liter" → "tej 2 liter": the quantity belongs to the item before it. */
    private fun mergeTrailingQuantities(chunks: List<List<String>>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        for (chunk in chunks) {
            if (isBareQuantity(chunk) && result.isNotEmpty()) result[result.lastIndex] = result.last() + chunk else result += chunk
        }
        return result
    }

    /** "2", "2 liter" – a number and at most a unit, without anything to buy. */
    private fun isBareQuantity(chunk: List<String>): Boolean =
        chunk.size <= 2 && NUMBER.matches(chunk.first()) && chunk.drop(1).all(QuantityParser::isUnit)

    /** Words with punctuation turned into [BREAK] markers; "1,5" keeps its comma. */
    private fun tokens(text: String): List<String> =
        text.replace(PUNCTUATION, " $BREAK ").trim().split(WHITESPACE).filter { it.isNotEmpty() }

    /** The digits for a (lowercase) number word, or null; "egy" counts only before a unit ("egy kiló"). */
    private fun numberOf(word: String, next: String?): String? = when (word) {
        "egy" -> if (next != null && QuantityParser.isUnit(next)) "1" else null
        else -> NUMBER_WORDS[word]
    }

    private companion object {
        const val BREAK = "\u0000"
        val WHITESPACE = Regex("\\s+")
        // A comma or full stop not between digits, and other sentence punctuation.
        val PUNCTUATION = Regex("""(?<!\d)[.,]|[.,](?!\d)|[;:!?]""")
        val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
        // Accent-folded: "meg" also covers "még".
        val JOINERS = setOf("es", "meg", "aztan", "utana", "valamint", "illetve", "plusz", "tovabba", "majd")
        val HU: Locale = Locale.forLanguageTag("hu-HU")
        val FILLERS = setOf("hát", "szóval", "izé", "öö", "ööö")
        val NUMBER_WORDS = mapOf(
            "két" to "2", "kettő" to "2", "három" to "3", "négy" to "4", "öt" to "5", "hat" to "6",
            "hét" to "7", "nyolc" to "8", "kilenc" to "9", "tíz" to "10", "tizenkét" to "12",
            "húsz" to "20", "fél" to "0,5", "másfél" to "1,5",
        )
    }
}
