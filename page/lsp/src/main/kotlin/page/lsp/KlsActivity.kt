package page.lsp

sealed interface KlsActivity {
    val kind: String

    data class Start(override val kind: String, val label: String) : KlsActivity
    data class End(override val kind: String) : KlsActivity
}

const val KLS_GRADLE_DEPS_KIND = "gradle-deps"
const val KLS_GRADLE_SCRIPT_DEPS_KIND = "gradle-script-deps"
const val KLS_SYMBOL_INDEX_KIND = "symbol-index"
const val KLS_LINTING_KIND = "linting"

private const val KLS_THREAD_PREFIX_WIDTH = 10

fun parseKlsActivity(raw: String?): KlsActivity? {
    val full = raw?.trim().orEmpty()
    if (full.isEmpty()) return null
    val msg = stripKlsThreadPrefix(full)
    return when {
        msg.contains("kotlinLSPProjectDeps", ignoreCase = true) ->
            KlsActivity.Start(KLS_GRADLE_DEPS_KIND, "Gradle: 프로젝트 의존성 해석 중…")
        msg.contains("kotlinLSPKotlinDSLDeps", ignoreCase = true) ->
            KlsActivity.Start(KLS_GRADLE_SCRIPT_DEPS_KIND, "Gradle: 빌드 스크립트 의존성 해석 중…")
        msg.startsWith("Successfully resolved build script", ignoreCase = true) ->
            KlsActivity.End(KLS_GRADLE_SCRIPT_DEPS_KIND)
        msg.startsWith("Successfully resolved", ignoreCase = true) ->
            KlsActivity.End(KLS_GRADLE_DEPS_KIND)
        msg.startsWith("Linting", ignoreCase = true) ->
            KlsActivity.Start(KLS_LINTING_KIND, "분석 중…")
        msg.startsWith("Updating full symbol index", ignoreCase = true) ->
            KlsActivity.Start(KLS_SYMBOL_INDEX_KIND, "심볼 인덱싱 중…")
        msg.startsWith("Updating symbol index", ignoreCase = true) ->
            KlsActivity.Start(KLS_SYMBOL_INDEX_KIND, "심볼 인덱싱 중…")
        msg.startsWith("Updated full symbol index", ignoreCase = true) ->
            KlsActivity.End(KLS_SYMBOL_INDEX_KIND)
        msg.startsWith("Updated symbol index", ignoreCase = true) ->
            KlsActivity.End(KLS_SYMBOL_INDEX_KIND)
        msg.startsWith("Reported", ignoreCase = true) && msg.contains("diagnostic", ignoreCase = true) ->
            KlsActivity.End(KLS_LINTING_KIND)
        msg.startsWith("No diagnostics", ignoreCase = true) ->
            KlsActivity.End(KLS_LINTING_KIND)
        else -> null
    }
}

private fun stripKlsThreadPrefix(s: String): String {
    if (s.length <= KLS_THREAD_PREFIX_WIDTH) return s
    return s.substring(KLS_THREAD_PREFIX_WIDTH).trimStart()
}
