package page.editor

object FoldRegions {
    data class Region(val startLine: Int, val endLine: Int)

    data class Segment(val origStart: Int, val origEnd: Int, val replacement: String)

    fun detect(text: String): List<Region> {
        val regions = mutableListOf<Region>()
        val stack = ArrayDeque<Int>()
        var line = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\n' -> { line++; i++ }
                c == '"' || c == '\'' -> {
                    val quote = c
                    i++
                    while (i < text.length) {
                        val cc = text[i]
                        if (cc == '\\' && i + 1 < text.length) {
                            if (text[i + 1] == '\n') line++
                            i += 2
                            continue
                        }
                        if (cc == '\n') { line++; i++; break }
                        if (cc == quote) { i++; break }
                        i++
                    }
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i < text.length) {
                        if (text[i] == '\n') { line++; i++ }
                        else if (text[i] == '*' && i + 1 < text.length && text[i + 1] == '/') { i += 2; break }
                        else i++
                    }
                }
                c == '{' -> { stack.addLast(line); i++ }
                c == '}' -> {
                    val openLine = stack.removeLastOrNull()
                    if (openLine != null && openLine < line) {
                        regions.add(Region(openLine, line))
                    }
                    i++
                }
                else -> i++
            }
        }
        return regions.sortedBy { it.startLine }
    }

    fun segmentsFor(text: String, foldedRegions: Collection<Region>): List<Segment> {
        if (foldedRegions.isEmpty()) return emptyList()
        val lineStarts = mutableListOf(0)
        for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
        val totalLines = lineStarts.size

        fun lineEndIndex(lineIdx: Int): Int =
            if (lineIdx + 1 < totalLines) lineStarts[lineIdx + 1] - 1 else text.length

        val sorted = foldedRegions.distinct().sortedBy { it.startLine }
        val out = mutableListOf<Segment>()
        var lastConsumedEnd = -1
        for (r in sorted) {
            if (r.startLine < 0 || r.endLine >= totalLines || r.endLine <= r.startLine) continue
            val origStart = lineEndIndex(r.startLine)
            val hasTrailingNewline = r.endLine + 1 < totalLines
            val origEnd = if (hasTrailingNewline) lineStarts[r.endLine + 1] else text.length
            val replacement = if (hasTrailingNewline) " ... }\n" else " ... }"
            if (origStart < lastConsumedEnd) continue
            out.add(Segment(origStart, origEnd, replacement))
            lastConsumedEnd = origEnd
        }
        return out
    }

    fun originalToTransformed(segments: List<Segment>, original: Int): Int {
        if (segments.isEmpty()) return original
        var savings = 0
        for (seg in segments) {
            if (original < seg.origStart) return original - savings
            if (original < seg.origEnd) return seg.origStart - savings
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        return original - savings
    }

    fun transformedToOriginal(segments: List<Segment>, transformed: Int): Int {
        if (segments.isEmpty()) return transformed
        var savings = 0
        for (seg in segments) {
            val transStart = seg.origStart - savings
            val transEnd = transStart + seg.replacement.length
            if (transformed < transStart) return transformed + savings
            if (transformed < transEnd) {
                val mid = transStart + seg.replacement.length / 2
                return if (transformed < mid) seg.origStart else seg.origEnd
            }
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        return transformed + savings
    }

    fun foldedRegionAt(
        text: String,
        foldedRegions: Collection<Region>,
        transformedOffset: Int,
    ): Region? {
        if (foldedRegions.isEmpty()) return null
        val sorted = foldedRegions.distinct().sortedBy { it.startLine }
        val segments = segmentsFor(text, sorted)
        if (segments.isEmpty()) return null
        var savings = 0
        var hit: Segment? = null
        for (seg in segments) {
            val transStart = seg.origStart - savings
            val dotsOffset = seg.replacement.indexOf("...")
            if (dotsOffset < 0) {
                savings += (seg.origEnd - seg.origStart) - seg.replacement.length
                continue
            }
            val dotsStart = transStart + dotsOffset
            val dotsEnd = dotsStart + 3
            if (transformedOffset < dotsStart) return null
            if (transformedOffset < dotsEnd) { hit = seg; break }
            savings += (seg.origEnd - seg.origStart) - seg.replacement.length
        }
        val target = hit ?: return null
        val lineStarts = mutableListOf(0)
        for (i in text.indices) if (text[i] == '\n') lineStarts.add(i + 1)
        val totalLines = lineStarts.size
        fun lineEndIndex(lineIdx: Int): Int =
            if (lineIdx + 1 < totalLines) lineStarts[lineIdx + 1] - 1 else text.length
        return sorted.firstOrNull { r ->
            r.startLine in 0 until totalLines &&
                r.endLine in 0 until totalLines &&
                r.endLine > r.startLine &&
                lineEndIndex(r.startLine) == target.origStart
        }
    }
}
