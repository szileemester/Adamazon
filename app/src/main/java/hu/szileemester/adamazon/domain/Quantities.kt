package hu.szileemester.adamazon.domain

import java.util.Locale
import kotlin.math.roundToLong

/** An amount with its unit, e.g. 1.5 + "l". An empty unit means a bare number. */
data class Quantity(val amount: Double, val unit: String)

/**
 * Quantities are stored as display text ("2 kg", "1,5 l"); this turns that text into a number +
 * unit for the editor's stepper and back. Text that is not a number ("egy kevés") stays free text.
 */
object Quantities {
    /** Units offered in the editor, most common first. */
    val UNITS = listOf(
        "db", "kg", "dkg", "g", "l", "dl", "ml",
        "csomag", "doboz", "üveg", "tálca", "tekercs", "pár", "szál", "fej", "gerezd", "szelet",
    )

    private val SYNONYMS = mapOf(
        "darab" to "db", "kiló" to "kg", "kilo" to "kg", "gramm" to "g", "liter" to "l",
        "cs" to "csomag", "csom" to "csomag", "deka" to "dkg", "dekagramm" to "dkg",
    )

    private val PATTERN = Regex("""^(\d+(?:[.,]\d+)?)\s*(\p{L}*)\.?$""")

    fun canonicalUnit(unit: String): String = unit.lowercase(Locale.ROOT).let { SYNONYMS[it] ?: it }

    /** "2 kg" → 2.0 kg; "1,5 l" → 1.5 l; "3" → 3.0 with no unit; anything else → null. */
    fun parse(text: String?): Quantity? {
        val match = PATTERN.matchEntire(text?.trim().orEmpty()) ?: return null
        val (number, unit) = match.destructured
        val amount = parseAmount(number) ?: return null
        return Quantity(amount, canonicalUnit(unit))
    }

    /** Accepts both decimal comma and point. */
    fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

    /** 2.0 → "2", 1.5 → "1,5" (Hungarian decimal comma), at most two decimals. */
    fun formatAmount(amount: Double): String {
        val hundredths = (amount * 100).roundToLong()
        if (hundredths % 100 == 0L) return (hundredths / 100).toString()
        return "%.2f".format(Locale.ROOT, hundredths / 100.0).trimEnd('0').replace('.', ',')
    }

    fun format(quantity: Quantity): String =
        listOf(formatAmount(quantity.amount), quantity.unit).filter { it.isNotEmpty() }.joinToString(" ")

    /** Stepper increment: whole pieces, half kilos/litres, 10 dkg, 100 g/ml. */
    fun step(unit: String): Double = when (canonicalUnit(unit)) {
        "kg", "l" -> 0.5
        "dkg" -> 10.0
        "g", "ml" -> 100.0
        else -> 1.0
    }

    /**
     * One stepper press: [direction] +1 or -1. Starting from nothing, "+" gives one step.
     * Going below one step returns null, i.e. "no quantity".
     */
    fun stepped(amount: Double?, unit: String, direction: Int): Double? {
        val step = step(unit)
        val next = (amount ?: 0.0) + direction * step
        return if (next < step - 1e-9) null else next
    }
}
