package hu.szileemester.adamazon.domain

/**
 * Splits pasted text into quick-add lines. Only line breaks separate items – a comma may be part of
 * an item ("1,5 l tej") – and list markers such as "- ", "• ", "1. " or "[ ] " are dropped.
 */
object PastedText {
    private val MARKER = Regex("""^\s*(?:[-*•·–—]|\d{1,3}[.)]|\[[ xX]?]|[☐☑✓✔])\s+""")

    /** True for pasted text with line breaks; typed input is always a single line. */
    fun isMultiLine(text: String): Boolean = text.contains('\n') && lines(text).isNotEmpty()

    fun lines(text: String): List<String> =
        text.split('\n', '\r')
            .map { MARKER.replace(it, "").trim() }
            .filter { it.isNotEmpty() }
}
