package hu.szileemester.adamazon.domain

import java.net.URI

/**
 * Where a store's website keeps its logo, best first: big touch icons, sized or vector site icons,
 * then `<img>` tags that call themselves a logo (often an SVG in the page header, like kik.hu's).
 */
object LogoCandidates {
    private val TAG = Regex("<(link|img)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([a-zA-Z-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

    fun fromHtml(html: String, pageUrl: String): List<String> {
        val ranked = mutableListOf<Pair<Int, String>>()
        TAG.findAll(html).forEach { match ->
            val tag = match.groupValues[1].lowercase()
            val attributes = ATTRIBUTE.findAll(match.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[2].trim('"', '\'')
            }
            val rank = if (tag == "link") linkRank(attributes) else imgRank(attributes)
            val target = attributes[if (tag == "link") "href" else "src"]
            if (rank != null && !target.isNullOrBlank() && !target.startsWith("data:")) {
                resolve(pageUrl, target)?.let { ranked += rank to it }
            }
        }
        return ranked.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun linkRank(a: Map<String, String>): Int? {
        val rel = a["rel"]?.lowercase() ?: return null
        val size = a["sizes"]?.substringBefore('x')?.toIntOrNull() ?: 0
        return when {
            "apple-touch-icon" in rel -> 0
            "icon" in rel && (a["type"]?.contains("svg") == true || a["href"]?.endsWith(".svg") == true) -> 1
            "icon" in rel && size >= 64 -> 2
            else -> null
        }
    }

    private fun imgRank(a: Map<String, String>): Int? {
        val described = listOf("src", "alt", "class", "id").joinToString(" ") { a[it].orEmpty() }.lowercase()
        return if ("logo" in described) 3 else null
    }

    private fun resolve(pageUrl: String, target: String): String? =
        runCatching { URI(pageUrl).resolve(target.replace(" ", "%20")).toString() }
            .getOrNull()?.takeIf { it.startsWith("http") }
}
