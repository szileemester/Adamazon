package hu.szileemester.adamazon.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRefTest {
    @Test
    fun `round trips an image id`() {
        val ref = IconRef.of("abc123")
        assertEquals("img:abc123", ref)
        assertTrue(IconRef.isImage(ref))
        assertEquals("abc123", IconRef.imageId(ref))
    }

    @Test
    fun `plain emoji is not an image`() {
        assertFalse(IconRef.isImage("🧀"))
        assertNull(IconRef.imageId("🧀"))
        assertNull(IconRef.imageId("img:"))
    }
}
