package hu.szileemester.adamazon.domain

/**
 * Turns dictated text into quick-add lines. Speech comes without punctuation ("két kiló alma tej
 * meg kenyér"), so an item ends at:
 * - a pause (each recognizer result is one utterance),
 * - a joining word ("és", "meg", "aztán"…) or punctuation, if the recognizer added any,
 * - a number, which starts the next item's quantity ("… alma három liter tej"),
 * - a second product word right after one ("tej kenyér"), unless the two form one product
 *   ("csirke comb") – adjectives such as "fokhagymás" are not product words.
 * Number words become digits so the quantity parser understands them.
 */
class VoiceSplitter(
    private val isProductWord: (String) -> Boolean,
    private val isOneProduct: (String, String) -> Boolean,
) {
    fun split(utterances: List<String>): List<String> = utterances.flatMap(::splitOne)

    private fun splitOne(utterance: String): List<String> {
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

            val next = words.getOrNull(index + 1)
            val number = numberOf(key, next)
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
        return mergeTrailingQuantities(chunks).map { it.joinToString(" ") }
    }

    /** "tej" + "2 liter" → "tej 2 liter": a bare quantity belongs to the item before it. */
    private fun mergeTrailingQuantities(chunks: List<List<String>>): List<List<String>> {
        val result = mutableListOf<MutableList<String>>()
        for (chunk in chunks) {
            val bareQuantity = NUMBER.matches(chunk.first()) && chunk.drop(1).all(QuantityParser::isUnit) && chunk.size <= 2
            if (bareQuantity && result.isNotEmpty()) result.last() += chunk else result += chunk.toMutableList()
        }
        return result
    }

    /** Words with punctuation turned into [BREAK] markers; "1,5" keeps its comma. */
    private fun tokens(text: String): List<String> =
        text.replace(PUNCTUATION, " $BREAK ").trim().split(WHITESPACE).filter { it.isNotEmpty() }

    /** The digits for a number word, or null; "egy" counts only before a unit ("egy kiló"). */
    private fun numberOf(key: String, next: String?): String? = when (key) {
        "egy" -> if (next != null && QuantityParser.isUnit(next)) "1" else null
        else -> NUMBER_WORDS[key]
    }

    private companion object {
        const val BREAK = "\u0000"
        val WHITESPACE = Regex("\\s+")
        // A comma or full stop not between digits, and other sentence punctuation.
        val PUNCTUATION = Regex("""(?<!\d)[.,]|[.,](?!\d)|[;:!?]""")
        val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
        // Accent-folded: "meg" also covers "még".
        val JOINERS = setOf("es", "meg", "aztan", "utana", "valamint", "illetve", "plusz", "tovabba", "majd")
        val NUMBER_WORDS = mapOf(
            "ket" to "2", "ketto" to "2", "harom" to "3", "negy" to "4", "ot" to "5", "hat" to "6",
            "het" to "7", "nyolc" to "8", "kilenc" to "9", "tiz" to "10", "tizenket" to "12",
            "husz" to "20", "fel" to "0,5", "masfel" to "1,5",
        )
    }
}
