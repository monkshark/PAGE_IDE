package page.editor

data class QuickOpenResult(
    val file: IndexedFile,
    val nameIndices: IntArray,
    val pathIndices: IntArray,
    val score: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QuickOpenResult) return false
        return score == other.score &&
            file == other.file &&
            nameIndices.contentEquals(other.nameIndices) &&
            pathIndices.contentEquals(other.pathIndices)
    }

    override fun hashCode(): Int {
        var h = file.hashCode()
        h = 31 * h + nameIndices.contentHashCode()
        h = 31 * h + pathIndices.contentHashCode()
        h = 31 * h + score
        return h
    }
}

object QuickOpen {

    private const val NAME_BONUS = 200
    private const val MAX_RESULTS = 50

    fun rank(query: String, files: List<IndexedFile>): List<QuickOpenResult> {
        if (query.isBlank()) {
            return files.take(MAX_RESULTS).map {
                QuickOpenResult(it, IntArray(0), IntArray(0), 0)
            }
        }
        val q = query.trim()
        val results = ArrayList<QuickOpenResult>(files.size)
        for (f in files) {
            val name = nameOf(f.relative)
            val nameMatch = FuzzyMatcher.match(q, name)
            val pathMatch = FuzzyMatcher.match(q, f.relative)
            if (nameMatch == null && pathMatch == null) continue
            val nameComponent = if (nameMatch != null) nameMatch.score + NAME_BONUS else 0
            val pathComponent = (pathMatch?.score ?: 0) / 4
            val score = nameComponent + pathComponent
            val nameIdx = nameMatch?.indices ?: IntArray(0)
            val pathIdx = pathMatch?.indices ?: IntArray(0)
            results += QuickOpenResult(f, nameIdx, pathIdx, score)
        }
        results.sortByDescending { it.score }
        return if (results.size <= MAX_RESULTS) results else results.subList(0, MAX_RESULTS).toList()
    }

    fun nameOf(relative: String): String {
        val slash = relative.lastIndexOf('/')
        return if (slash < 0) relative else relative.substring(slash + 1)
    }

    fun nameOffset(relative: String): Int {
        val slash = relative.lastIndexOf('/')
        return if (slash < 0) 0 else slash + 1
    }
}
