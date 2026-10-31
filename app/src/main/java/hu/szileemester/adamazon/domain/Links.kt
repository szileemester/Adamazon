package hu.szileemester.adamazon.domain

/** A web link found in free text: where it is, and the address to open. */
data class FoundLink(val range: IntRange, val url: String)

object Links {
    private val CANDIDATE = Regex("""(?i)\b(?:https?://|www\.)\S+""")
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}\"'”"

    /** Finds http(s):// and www. links; trailing punctuation ("…/akcio.") is not part of the link. */
    fun find(text: String): List<FoundLink> = CANDIDATE.findAll(text).mapNotNull { match ->
        val raw = match.value.trimEnd { it in TRAILING_PUNCTUATION }
        if (raw.length < 5 || !raw.contains('.')) return@mapNotNull null
        FoundLink(match.range.first until match.range.first + raw.length, toUrl(raw))
    }.toList()

    /**
     * Android matches URL schemes case-sensitively, and the keyboard's auto-capitalisation turns a
     * sentence-initial "https://" into "Https://", which no browser would open – so lowercase it.
     */
    private fun toUrl(raw: String): String {
        val scheme = raw.substringBefore("://", missingDelimiterValue = "")
        return when {
            scheme.isEmpty() -> "https://$raw" // www.…
            else -> scheme.lowercase() + raw.substring(scheme.length)
        }
    }
}
