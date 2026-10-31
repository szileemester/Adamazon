package hu.szileemester.adamazon.domain

data class ParsedInput(val name: String, val quantity: String?)

/** Splits quick-add input such as "2 kg paradicsom" or "tej 2 l" into name and quantity. */
object QuantityParser {
    private const val NUMBER = "\\d+(?:[.,]\\d+)?"
    private const val UNITS =
        "kg|dkg|g|gramm|l|liter|dl|cl|ml|db|darab|cs|csomag|csom|üveg|doboz|tálca|zacskó|szál|fej|gerezd|tekercs|pár|kiló|rekesz|karton|tubus|flakon|szelet"
    private val LEADING =
        Regex("^($NUMBER)\\s*(?:($UNITS)\\.?|x|×)?\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val TRAILING =
        Regex("^(.+?)\\s+($NUMBER)\\s*(?:($UNITS)\\.?|x|×)?$", RegexOption.IGNORE_CASE)

    fun parse(input: String): ParsedInput {
        val text = input.trim().replace(Regex("\\s+"), " ")
        LEADING.matchEntire(text)?.let { m ->
            val (amount, unit, name) = m.destructured
            return ParsedInput(Normalizer.clean(name), quantity(amount, unit))
        }
        TRAILING.matchEntire(text)?.let { m ->
            val (name, amount, unit) = m.destructured
            return ParsedInput(Normalizer.clean(name), quantity(amount, unit))
        }
        return ParsedInput(Normalizer.clean(text), null)
    }

    private fun quantity(amount: String, unit: String): String =
        if (unit.isEmpty()) "$amount db" else "$amount ${Quantities.canonicalUnit(unit)}"
}
