package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SplitPaneStateTest {
    @Test
    fun defaultRatioIsHalfAndWithinBounds() {
        val s = SplitPaneState()
        assertEquals(0.5f, s.ratio)
        assertEquals(0.5f, s.clamped)
    }

    @Test
    fun withRatioClampsToMinAndMax() {
        val s = SplitPaneState(minRatio = 0.2f, maxRatio = 0.8f)
        assertEquals(0.2f, s.withRatio(-1f).ratio)
        assertEquals(0.8f, s.withRatio(2f).ratio)
        assertEquals(0.6f, s.withRatio(0.6f).ratio)
    }

    @Test
    fun dragByConvertsPixelsToRatio() {
        val s = SplitPaneState(ratio = 0.5f)
        val dragged = s.dragBy(deltaPx = 100f, totalPx = 1000f)
        assertEquals(0.6f, dragged.ratio, "100/1000 added to 0.5")
    }

    @Test
    fun dragByRespectsBounds() {
        val s = SplitPaneState(ratio = 0.85f, minRatio = 0.1f, maxRatio = 0.9f)
        val dragged = s.dragBy(deltaPx = 500f, totalPx = 1000f)
        assertEquals(0.9f, dragged.ratio)
    }

    @Test
    fun dragByZeroTotalIsNoOp() {
        val s = SplitPaneState(ratio = 0.5f)
        assertEquals(s, s.dragBy(deltaPx = 100f, totalPx = 0f))
    }

    @Test
    fun firstAndSecondPanePxSumToTotal() {
        val s = SplitPaneState(ratio = 0.3f)
        val total = 1000f
        assertEquals(total, s.firstPanePx(total) + s.secondPanePx(total))
    }

    @Test
    fun invalidBoundsReject() {
        assertFailsWith<IllegalArgumentException> {
            SplitPaneState(minRatio = 0.7f, maxRatio = 0.3f)
        }
        assertFailsWith<IllegalArgumentException> {
            SplitPaneState(minRatio = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            SplitPaneState(maxRatio = 1.5f)
        }
    }

    @Test
    fun clampedExposesValueInRange() {
        val s = SplitPaneState(ratio = 0.95f, maxRatio = 0.9f)
        assertTrue(s.clamped <= 0.9f)
    }
}
