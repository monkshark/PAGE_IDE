package page.editor

object FuzzyMatcher {

    data class Match(val score: Int, val indices: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Match) return false
            return score == other.score && indices.contentEquals(other.indices)
        }
        override fun hashCode(): Int = 31 * score + indices.contentHashCode()
    }

    fun match(query: String, target: String): Match? {
        if (query.isEmpty()) return Match(0, IntArray(0))
        if (target.length < query.length) return null

        val q = query.lowercase()
        val t = target.lowercase()
        val indices = IntArray(q.length)
        var score = 0
        var qi = 0
        var prevIdx = -2
        var streak = 0

        for (ti in t.indices) {
            if (qi >= q.length) break
            if (t[ti] != q[qi]) continue

            indices[qi] = ti
            var bonus = 0
            if (ti == prevIdx + 1) {
                streak++
                bonus += 15 + streak * 5
            } else {
                streak = 0
            }
            if (isWordBoundary(target, ti)) bonus += 30
            if (ti == 0) bonus += 20
            if (target[ti] == query[qi]) bonus += 5

            score += bonus + 1
            prevIdx = ti
            qi++
        }

        if (qi < q.length) return null
        score -= (target.length - q.length)
        return Match(score, indices)
    }

    private fun isWordBoundary(target: String, idx: Int): Boolean {
        if (idx == 0) return true
        val prev = target[idx - 1]
        val curr = target[idx]
        if (prev == '/' || prev == '\\' || prev == '.' || prev == '_' || prev == '-' || prev == ' ') return true
        if (prev.isLowerCase() && curr.isUpperCase()) return true
        return false
    }
}
