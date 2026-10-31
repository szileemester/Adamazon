package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedTextTest {
    @Test
    fun `splits on line breaks only`() {
        assertEquals(listOf("tej", "1,5 l narancslé", "Édesség, snack"), PastedText.lines("tej\n1,5 l narancslé\r\nÉdesség, snack"))
    }

    @Test
    fun `drops list markers and empty lines`() {
        val text = """
            - tej
            • kenyér

            1. 2 kg alma
            2) sajt
            [ ] vaj
            [x] tojás
        """.trimIndent()
        assertEquals(listOf("tej", "kenyér", "2 kg alma", "sajt", "vaj", "tojás"), PastedText.lines(text))
    }

    @Test
    fun `numbers that are quantities are kept`() {
        assertEquals(listOf("2 kg alma", "10 tojás"), PastedText.lines("2 kg alma\n10 tojás"))
    }

    @Test
    fun `detects multi-line input`() {
        assertTrue(PastedText.isMultiLine("tej\nkenyér"))
        assertFalse(PastedText.isMultiLine("tej"))
    }
}
