package hu.szileemester.adamazon.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSplitterTest {
    private val categorizer = Categorizer(
        DictionaryParser.parse(File("src/main/assets/dictionary.json").readText()),
    )
    private val splitter = VoiceSplitter(categorizer::isProductWord, categorizer::isOneProduct)

    private fun split(vararg utterances: String) = splitter.split(utterances.toList())

    @Test
    fun `each pause is an item`() {
        assertEquals(listOf("alma", "fokhagymás kenyér"), split("alma", "fokhagymás kenyér"))
    }

    @Test
    fun `joining words and punctuation end an item`() {
        assertEquals(listOf("alma", "tej", "kenyér"), split("alma és tej meg kenyér"))
        assertEquals(listOf("Alma", "tej", "Kenyér"), split("Alma, tej. Kenyér"))
        assertEquals(listOf("vaj", "1 doboz tojás"), split("vaj és még egy doboz tojás"))
    }

    @Test
    fun `a number starts the next item`() {
        assertEquals(listOf("2 kiló alma", "3 liter tej"), split("két kiló alma három liter tej"))
        assertEquals(listOf("0,5 kg darált hús", "1,5 l kóla"), split("fél kg darált hús másfél l kóla"))
        assertEquals(listOf("1,5 l kóla", "tej"), split("1,5 l kóla, tej"))
    }

    @Test
    fun `a trailing quantity stays with its item`() {
        assertEquals(listOf("tej 2 liter", "kenyér"), split("tej 2 liter és kenyér"))
    }

    @Test
    fun `two product words in a row are two items`() {
        assertEquals(listOf("tej", "kenyér", "vaj"), split("tej kenyér vaj"))
        assertEquals(listOf("2 kiló alma", "tej"), split("két kiló alma tej"))
    }

    @Test
    fun `adjectives and multi-word products stay together`() {
        assertEquals(listOf("fokhagymás kenyér"), split("fokhagymás kenyér"))
        assertEquals(listOf("almás pite"), split("almás pite"))
        assertEquals(listOf("kovászos uborka", "sajt"), split("kovászos uborka sajt"))
    }

    @Test
    fun `an article is dropped, not a quantity`() {
        assertEquals(listOf("tej", "kenyér"), split("egy tej és egy kenyér"))
    }

    @Test
    fun `the result parses into name and quantity`() {
        val parsed = split("két kiló alma").map(QuantityParser::parse)
        assertEquals(listOf(ParsedInput("Alma", "2 kg")), parsed)
        assertEquals(ParsedInput("Szalámi", "20 dkg"), QuantityParser.parse(split("húsz deka szalámi").single()))
    }
}
