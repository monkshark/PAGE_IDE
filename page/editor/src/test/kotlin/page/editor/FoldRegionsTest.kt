package page.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoldRegionsTest {
    @Test
    fun detectSimpleBlock() {
        val text = "class A {\n    val x = 1\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(0, 2)), regions)
    }

    @Test
    fun detectNestedBlocks() {
        val text = "fun f() {\n    if (x) {\n        y\n    }\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(
            listOf(FoldRegions.Region(0, 4), FoldRegions.Region(1, 3)),
            regions.sortedBy { it.startLine }.sortedBy { it.startLine }
        )
    }

    @Test
    fun ignoreSingleLineBlocks() {
        val text = "fun f() { return 1 }\n"
        val regions = FoldRegions.detect(text)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun ignoreBracesInsideStrings() {
        val text = "val s = \"{ not a block }\"\nclass A {\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(1, 2)), regions)
    }

    @Test
    fun ignoreBracesInsideLineComments() {
        val text = "// { fake }\nclass A {\n    body\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(1, 3)), regions)
    }

    @Test
    fun ignoreBracesInsideBlockComments() {
        val text = "/* {\nfake\n} */\nclass A {\n}\n"
        val regions = FoldRegions.detect(text)
        assertEquals(listOf(FoldRegions.Region(3, 4)), regions)
    }

    @Test
    fun unbalancedBracesIgnored() {
        val text = "}{}{\n"
        val regions = FoldRegions.detect(text)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun segmentForBlock() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        val seg = segs[0]
        assertEquals(16, seg.origStart)
        assertEquals(26, seg.origEnd)
        assertEquals(" ... }\n", seg.replacement)
    }

    @Test
    fun segmentForLastLineBlock() {
        val text = "function foo() {\n  body\n}"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        assertEquals(1, segs.size)
        val seg = segs[0]
        assertEquals(16, seg.origStart)
        assertEquals(text.length, seg.origEnd)
        assertEquals(" ... }", seg.replacement)
    }

    @Test
    fun segmentsAreSortedAndNonOverlapping() {
        val text = "fun f() {\n    if (x) {\n        y\n    }\n}\n"
        val outer = FoldRegions.Region(0, 4)
        val inner = FoldRegions.Region(1, 3)
        val segs = FoldRegions.segmentsFor(text, listOf(outer, inner))
        assertEquals(1, segs.size, "outer fold should swallow inner")
    }

    @Test
    fun originalToTransformedBeforeFold() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        assertEquals(5, FoldRegions.originalToTransformed(segs, 5))
    }

    @Test
    fun originalToTransformedInsideFoldClampsToStart() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        assertEquals(16, FoldRegions.originalToTransformed(segs, 20))
    }

    @Test
    fun originalToTransformedAfterFoldShifts() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val origRest = text.indexOf("rest")
        val transformedRest = FoldRegions.originalToTransformed(segs, origRest)
        assertEquals(16 + " ... }\n".length, transformedRest)
    }

    @Test
    fun transformedToOriginalLeftHalfMapsToFoldStart() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val transStart = segs[0].origStart
        val mid = transStart + segs[0].replacement.length / 2
        for (off in transStart until mid) {
            assertEquals(segs[0].origStart, FoldRegions.transformedToOriginal(segs, off),
                "left-half offset $off should map to origStart")
        }
    }

    @Test
    fun transformedToOriginalRightHalfMapsToFoldEnd() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val transStart = segs[0].origStart
        val mid = transStart + segs[0].replacement.length / 2
        val transEnd = transStart + segs[0].replacement.length
        for (off in mid until transEnd) {
            assertEquals(segs[0].origEnd, FoldRegions.transformedToOriginal(segs, off),
                "right-half offset $off should map to origEnd")
        }
    }

    @Test
    fun transformedToOriginalRoundTrip() {
        val text = "function foo() {\n  body\n}\nrest"
        val segs = FoldRegions.segmentsFor(text, listOf(FoldRegions.Region(0, 2)))
        val origRest = text.indexOf("rest")
        val t = FoldRegions.originalToTransformed(segs, origRest)
        assertEquals(origRest, FoldRegions.transformedToOriginal(segs, t))
    }

    @Test
    fun foldedRegionAtDotsHits() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val dotsStart = segs[0].origStart + segs[0].replacement.indexOf("...")
        for (off in dotsStart until dotsStart + 3) {
            val hit = FoldRegions.foldedRegionAt(text, listOf(region), off)
            assertEquals(region, hit, "expected hit at offset $off")
        }
    }

    @Test
    fun foldedRegionAtBraceAndSpacesMiss() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val transStart = segs[0].origStart
        val rep = segs[0].replacement
        val leadingSpace = transStart + rep.indexOf(' ')
        val dotsStart = transStart + rep.indexOf("...")
        val trailingSpace = dotsStart + 3
        val brace = transStart + rep.indexOf('}')
        for (off in listOf(leadingSpace, trailingSpace, brace)) {
            val hit = FoldRegions.foldedRegionAt(text, listOf(region), off)
            assertEquals(null, hit, "expected miss at offset $off")
        }
    }

    @Test
    fun foldedRegionAtOutsidePlaceholderMisses() {
        val text = "function foo() {\n  body\n}\nrest"
        val region = FoldRegions.Region(0, 2)
        val before = FoldRegions.foldedRegionAt(text, listOf(region), 0)
        assertEquals(null, before)
        val segs = FoldRegions.segmentsFor(text, listOf(region))
        val past = segs[0].origStart + segs[0].replacement.length + 1
        val after = FoldRegions.foldedRegionAt(text, listOf(region), past)
        assertEquals(null, after)
    }
}
