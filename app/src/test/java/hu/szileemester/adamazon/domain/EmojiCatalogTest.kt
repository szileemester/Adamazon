package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmojiCatalogTest {
    private val sample = EmojiCatalog.parse(
        """[
            {"e":"🐕","g":"nature","n":"kutya","k":"állat|háziállat"},
            {"e":"🧀","g":"food","n":"sajtdarab","k":"ementáli|étel|sajt"},
            {"e":"🧼","g":"objects","n":"szappan","k":"tiszta|tisztálkodás"},
            {"e":"🥛","g":"food","n":"egy pohár tej","k":"ital|tej"}
        ]""",
    )

    @Test
    fun `parses entries and groups in order`() {
        assertEquals(4, sample.entries.size)
        assertEquals(listOf("nature", "food", "objects"), sample.groups)
        assertEquals(listOf("🧀", "🥛"), sample.inGroup("food").map { it.emoji })
        assertEquals(listOf("állat", "háziállat"), sample.entries.first().keywords)
    }

    @Test
    fun `search ignores accents and case`() {
        assertEquals(listOf("🧼"), sample.search("TISZTÁLKODÁS").map { it.emoji })
        assertEquals(listOf("🧼"), sample.search("tisztalkodas").map { it.emoji })
        assertEquals(listOf("🐕"), sample.search("haziallat").map { it.emoji })
    }

    @Test
    fun `name matches come before keyword matches`() {
        assertEquals(listOf("🧀"), sample.search("sajt").map { it.emoji })
        assertEquals("🥛", sample.search("tej").first().emoji)
    }

    @Test
    fun `every word must match and blank finds nothing`() {
        assertEquals(listOf("🥛"), sample.search("pohár ital").map { it.emoji })
        assertTrue(sample.search("kutya ital").isEmpty())
        assertTrue(sample.search("  ").isEmpty())
    }

    @Test
    fun `the bundled catalogue finds common shopping words`() {
        val catalog = EmojiCatalog.parse(File("src/main/assets/emoji_hu.json").readText())
        assertTrue(catalog.entries.size > 1500)
        listOf("sajt" to "🧀", "kutya" to "🐕", "szappan" to "🧼", "kenyer" to "🍞").forEach { (query, emoji) ->
            assertTrue("$query → $emoji", catalog.search(query).any { it.emoji == emoji })
        }
    }
}
