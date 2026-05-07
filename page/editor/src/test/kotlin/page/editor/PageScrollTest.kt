package page.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PageScrollTest {
    @Test fun fallsBackWhenViewportUnknown() {
        assertEquals(10, PageScroll.linesPerPage(viewportPx = 0f, lineHeightPx = 20f))
        assertEquals(10, PageScroll.linesPerPage(viewportPx = -50f, lineHeightPx = 20f))
        assertEquals(10, PageScroll.linesPerPage(viewportPx = 400f, lineHeightPx = 0f))
    }

    @Test fun standardViewportLeavesContextLine() {
        assertEquals(19, PageScroll.linesPerPage(viewportPx = 400f, lineHeightPx = 20f))
    }

    @Test fun smallViewportNeverDropsBelowOneLine() {
        assertEquals(1, PageScroll.linesPerPage(viewportPx = 20f, lineHeightPx = 20f))
        assertEquals(1, PageScroll.linesPerPage(viewportPx = 30f, lineHeightPx = 20f))
        assertEquals(1, PageScroll.linesPerPage(viewportPx = 1f, lineHeightPx = 20f))
    }

    @Test fun honoursCustomFallback() {
        assertEquals(5, PageScroll.linesPerPage(viewportPx = 0f, lineHeightPx = 20f, fallback = 5))
    }

    @Test fun fractionalViewportRoundsDown() {
        assertEquals(6, PageScroll.linesPerPage(viewportPx = 159f, lineHeightPx = 20f))
    }
}
