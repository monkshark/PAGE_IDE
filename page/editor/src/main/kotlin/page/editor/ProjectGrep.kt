package page.editor

import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class GrepHit(
    val offset: Int,
    val line: Int,
    val col: Int,
    val lineText: String,
    val matchStart: Int,
    val matchEnd: Int,
    val matchLength: Int,
)

data class GrepFileResult(
    val file: IndexedFile,
    val hits: List<GrepHit>,
)

data class GrepStats(
    val files: Int,
    val hits: Int,
    val skippedBinary: Int,
    val truncated: Boolean,
    val patternInvalid: Boolean = false,
)

data class GrepReport(
    val results: List<GrepFileResult>,
    val stats: GrepStats,
)

data class ReplaceReport(
    val filesChanged: Int,
    val replacements: Int,
)

object ProjectGrep {

    private const val MAX_HITS_PER_FILE = 200
    private const val MAX_TOTAL_HITS = 5000
    private const val MAX_LINE_LENGTH = 4000
    private const val BINARY_PROBE = 8000

    fun search(
        files: List<IndexedFile>,
        query: String,
        caseSensitive: Boolean,
        regex: Boolean = false,
        wholeWord: Boolean = false,
        loader: (Path) -> String?,
        cancelled: () -> Boolean = { false },
    ): GrepReport {
        if (query.isEmpty()) {
            return GrepReport(emptyList(), GrepStats(0, 0, 0, false))
        }
        val pattern = buildPattern(query, regex, wholeWord, caseSensitive)
            ?: return GrepReport(
                emptyList(),
                GrepStats(0, 0, 0, truncated = false, patternInvalid = true),
            )
        val results = ArrayList<GrepFileResult>()
        var totalHits = 0
        var skippedBinary = 0
        var truncated = false
        for (f in files) {
            if (cancelled()) break
            if (totalHits >= MAX_TOTAL_HITS) {
                truncated = true
                break
            }
            if (!FileKinds.classify(f.path).isEditableAsText) continue
            val text = loader(f.path) ?: continue
            if (looksBinary(text)) {
                skippedBinary++
                continue
            }
            val budget = (MAX_TOTAL_HITS - totalHits).coerceAtMost(MAX_HITS_PER_FILE)
            val hits = scan(text, pattern, budget)
            if (hits.isNotEmpty()) {
                results += GrepFileResult(f, hits)
                totalHits += hits.size
            }
        }
        return GrepReport(
            results = results,
            stats = GrepStats(
                files = results.size,
                hits = totalHits,
                skippedBinary = skippedBinary,
                truncated = truncated,
            ),
        )
    }

    fun applyReplace(
        text: String,
        query: String,
        replacement: String,
        caseSensitive: Boolean,
        regex: Boolean = false,
        wholeWord: Boolean = false,
    ): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        val pattern = buildPattern(query, regex, wholeWord, caseSensitive) ?: return text to 0
        val matcher = pattern.matcher(text)
        val sb = StringBuffer(text.length)
        val safeReplacement = if (regex) replacement else Matcher.quoteReplacement(replacement)
        var count = 0
        while (matcher.find()) {
            try {
                matcher.appendReplacement(sb, safeReplacement)
            } catch (_: IndexOutOfBoundsException) {
                return text to 0
            } catch (_: IllegalArgumentException) {
                return text to 0
            }
            count++
        }
        matcher.appendTail(sb)
        return sb.toString() to count
    }

    fun isValidPattern(
        query: String,
        regex: Boolean,
        wholeWord: Boolean,
        caseSensitive: Boolean,
    ): Boolean {
        if (query.isEmpty()) return true
        return buildPattern(query, regex, wholeWord, caseSensitive) != null
    }

    private fun buildPattern(
        query: String,
        regex: Boolean,
        wholeWord: Boolean,
        caseSensitive: Boolean,
    ): Pattern? = try {
        val core = if (regex) query else Pattern.quote(query)
        val wrapped = if (wholeWord) "\\b(?:$core)\\b" else core
        var flags = Pattern.UNICODE_CHARACTER_CLASS
        if (!caseSensitive) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        Pattern.compile(wrapped, flags)
    } catch (_: PatternSyntaxException) {
        null
    }

    private fun scan(
        text: String,
        pattern: Pattern,
        budget: Int,
    ): List<GrepHit> {
        if (budget <= 0) return emptyList()
        val out = ArrayList<GrepHit>()
        val matcher = pattern.matcher(text)
        var line = 0
        var lineStart = 0
        var nextNewline = text.indexOf('\n')
        var searchFrom = 0
        while (matcher.find(searchFrom)) {
            val start = matcher.start()
            val end = matcher.end()
            while (nextNewline in 0 until start) {
                line++
                lineStart = nextNewline + 1
                nextNewline = text.indexOf('\n', lineStart)
            }
            val lineEnd = if (nextNewline >= 0) nextNewline else text.length
            val rawLine = text.substring(lineStart, lineEnd)
            val matchInLine = start - lineStart
            val matchLen = end - start
            val (clipped, clippedStart) = clipLine(rawLine, matchInLine)
            out += GrepHit(
                offset = start,
                line = line,
                col = matchInLine,
                lineText = clipped,
                matchStart = clippedStart,
                matchEnd = (clippedStart + matchLen).coerceAtMost(clipped.length),
                matchLength = matchLen,
            )
            if (out.size >= budget) break
            searchFrom = if (end == start) end + 1 else end
            if (searchFrom > text.length) break
        }
        return out
    }

    private fun clipLine(line: String, matchInLine: Int): Pair<String, Int> {
        if (line.length <= MAX_LINE_LENGTH) return line to matchInLine
        val window = MAX_LINE_LENGTH
        val half = window / 2
        val start = (matchInLine - half).coerceAtLeast(0)
        val end = (start + window).coerceAtMost(line.length)
        val realStart = (end - window).coerceAtLeast(0)
        return line.substring(realStart, end) to (matchInLine - realStart)
    }

    private fun looksBinary(text: String): Boolean {
        val limit = BINARY_PROBE.coerceAtMost(text.length)
        for (i in 0 until limit) {
            if (text[i].code == 0) return true
        }
        return false
    }
}
