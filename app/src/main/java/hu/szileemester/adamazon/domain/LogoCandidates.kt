package hu.szileemester.adamazon.domain

import java.net.URL

/**
 * Where a store's website keeps its logo, best first: big touch icons, sized or vector site icons,
 * then `<img>` tags that call themselves a logo (often an SVG in the page header, like kik.hu's).
 */
object LogoCandidates {
    fun fromHtml(html: String, pageUrl: String): List<String> {
        val ranked = mutableListOf<Pair<Int, String>>()
        HtmlTags.find(html, "link", "img").forEach { (tag, attributes) ->
            val rank = if (tag == "link") linkRank(attributes) else imgRank(attributes)
            val target = attributes[if (tag == "link") "href" else "src"]
            if (rank != null && target != null) HtmlTags.resolve(pageUrl, target)?.let { ranked += rank to it }
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
}

/**
 * The picture a web page shares itself with (what chat apps show for a pasted link): a shop's
 * product page gives the product photo. Falls back to the page's logo ([LogoCandidates]).
 */
object PageImage {
    private val SHARE_PROPERTIES = listOf("og:image:secure_url", "og:image", "twitter:image")

    fun fromHtml(html: String, pageUrl: String): List<String> {
        val metas = HtmlTags.find(html, "meta").map { it.second }
        val shared = SHARE_PROPERTIES.mapNotNull { property ->
            metas.firstOrNull { (it["property"] ?: it["name"])?.lowercase() == property }?.get("content")
        }.mapNotNull { HtmlTags.resolve(pageUrl, it) }
        return (shared + LogoCandidates.fromHtml(html, pageUrl)).distinct()
    }
}

/** Just enough HTML reading to find tags and their attributes, without a parser library. */
internal object HtmlTags {
    private val ATTRIBUTE = Regex("([a-zA-Z:_-]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

    /** (tag, attributes) of every [names] tag, in document order; attribute names lowercased. */
    fun find(html: String, vararg names: String): List<Pair<String, Map<String, String>>> {
        val tag = Regex("<(${names.joinToString("|")})\\b[^>]*>", RegexOption.IGNORE_CASE)
        return tag.findAll(html).map { match ->
            match.groupValues[1].lowercase() to ATTRIBUTE.findAll(match.value).associate {
                it.groupValues[1].lowercase() to it.groupValues[2].trim('"', '\'').replace("&amp;", "&")
            }
        }.toList()
    }

    /** An absolute http(s) address for [target] on [pageUrl]; null for inline (data:) images. */
    fun resolve(pageUrl: String, target: String): String? {
        if (target.isBlank() || target.startsWith("data:")) return null
        // URL, not URI: it also takes the unencoded spaces and accents that real pages contain.
        return runCatching { URL(URL(pageUrl), target.trim()).toString() }
            .getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
