package page.app.utils

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorUtilsTest {

    @Test
    fun `offsetToLineChar counts lines and columns`() {
        val text = "ab\ncde\nf"
        assertEquals(0 to 0, offsetToLineChar(text, 0))
        assertEquals(0 to 2, offsetToLineChar(text, 2))
        assertEquals(1 to 0, offsetToLineChar(text, 3))
        assertEquals(1 to 2, offsetToLineChar(text, 5))
        assertEquals(2 to 1, offsetToLineChar(text, 8))
    }

    @Test
    fun `offsetToLineChar clamps out of range offsets`() {
        val text = "abc"
        assertEquals(0 to 0, offsetToLineChar(text, -5))
        assertEquals(0 to 3, offsetToLineChar(text, 99))
    }

    @Test
    fun `isKotlinSource matches kt and kts case insensitively`() {
        assertTrue(isKotlinSource(Paths.get("Main.kt")))
        assertTrue(isKotlinSource(Paths.get("build.gradle.kts")))
        assertTrue(isKotlinSource(Paths.get("DIR/Upper.KT")))
        assertFalse(isKotlinSource(Paths.get("Main.java")))
        assertFalse(isKotlinSource(Paths.get("README")))
    }

    @Test
    fun `windowTitle falls back to untitled for null path`() {
        assertTrue(windowTitle(null).startsWith("untitled — "))
        assertTrue(windowTitle(Paths.get("dir/Hello.kt")).startsWith("Hello.kt — "))
    }
}
