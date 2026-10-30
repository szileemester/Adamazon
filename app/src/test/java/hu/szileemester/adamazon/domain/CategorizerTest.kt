package hu.szileemester.adamazon.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorizerTest {
    private val categorizer = Categorizer(
        DictionaryParser.parse(File("src/main/assets/dictionary.json").readText()),
    )
    private val categoryEmoji = mapOf("egyeb" to "🛒", "zoldseg" to "🥬")

    private fun guess(name: String, learned: Map<String, LearnedMapping> = emptyMap()) =
        categorizer.guess(name, learned) { categoryEmoji[it] }

    private fun assertCategory(name: String, categoryId: String, emoji: String? = null) {
        val result = guess(name)
        assertEquals("category of '$name'", categoryId, result.categoryId)
        if (emoji != null) assertEquals("emoji of '$name'", emoji, result.emoji)
    }

    @Test
    fun `basic products`() {
        assertCategory("Paradicsom", "zoldseg", "🍅")
        assertCategory("WC papír", "drogeria", "🧻")
        assertCategory("wc-papír", "drogeria", "🧻")
        assertCategory("Toalettpapír", "drogeria")
        assertCategory("Kenyér", "pekaru", "🍞")
        assertCategory("Tej", "tejtermek", "🥛")
        assertCategory("Sör", "ital", "🍺")
        assertCategory("Só", "tartos", "🧂")
    }

    @Test
    fun `suffixes and lengthened stems`() {
        assertCategory("paradicsomot", "zoldseg")
        assertCategory("almát", "gyumolcs")
        assertCategory("kenyeret", "pekaru")
        assertCategory("tojást", "tejtermek")
        assertCategory("sót", "tartos")
    }

    @Test
    fun `longest stem wins over its prefix`() {
        assertCategory("Tejföl", "tejtermek")
        assertCategory("Borsó", "zoldseg")
        assertCategory("Sonka", "hus")
        assertCategory("Sült krumpli", "fagyasztott", "🍟")
        assertCategory("Mosogatógép só", "tisztito")
        assertCategory("Szájvíz", "drogeria")
        assertCategory("Naptej", "drogeria")
    }

    @Test
    fun `short stems do not match long unrelated words`() {
        assertCategory("Halloumi", "egyeb")
    }

    @Test
    fun `adjectives before the head noun`() {
        assertCategory("Laktózmentes tej", "tejtermek")
        assertCategory("Fokhagymás kenyér", "pekaru")
        assertCategory("Zöld alma", "gyumolcs")
    }

    @Test
    fun `unknown items fall back to Egyeb with its emoji`() {
        val result = guess("Xyzzy")
        assertEquals(CategoryGuess("egyeb", "🛒", CategoryGuess.Source.FALLBACK), result)
    }

    @Test
    fun `learned mapping overrides the dictionary`() {
        val learned = mapOf(Normalizer.key("Paradicsom") to LearnedMapping("Paradicsom", "gyumolcs", "🍎"))
        assertEquals(
            CategoryGuess("gyumolcs", "🍎", CategoryGuess.Source.LEARNED),
            guess("paradicsom", learned),
        )
    }
}
