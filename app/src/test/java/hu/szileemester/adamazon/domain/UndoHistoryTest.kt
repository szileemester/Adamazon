package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UndoHistoryTest {
    private fun entry(n: Int) = UndoEntry("lépés $n", emptyMap())

    @Test
    fun `undo returns the newest change first`() {
        val history = UndoHistory().push(entry(1)).push(entry(2))
        val (last, rest) = history.pop()!!
        assertEquals("lépés 2", last.label)
        assertEquals("lépés 1", rest.pop()!!.first.label)
    }

    @Test
    fun `only the last five changes are kept`() {
        var history = UndoHistory()
        (1..7).forEach { history = history.push(entry(it)) }
        assertEquals((3..7).map { "lépés $it" }, history.entries.map { it.label })
    }

    @Test
    fun `empty history has nothing to undo`() {
        assertFalse(UndoHistory().canUndo)
        assertNull(UndoHistory().pop())
    }
}
