package page.runtime

import java.nio.file.Files
import java.nio.file.Path

object FlutterProjectDetector {

    private val sdkFlutterDependency = Regex("(?m)^\\s+sdk:\\s*flutter\\b")
    private val topLevelFlutterSection = Regex("(?m)^flutter:\\s*(#.*)?$")

    fun isFlutterPubspec(content: String): Boolean =
        sdkFlutterDependency.containsMatchIn(content) || topLevelFlutterSection.containsMatchIn(content)

    fun findPubspec(start: Path, ceiling: Path? = null): Path? {
        val stop = ceiling?.toAbsolutePath()?.normalize()
        var dir: Path? = (if (Files.isDirectory(start)) start else start.parent)
            ?.toAbsolutePath()?.normalize()
        while (dir != null) {
            val candidate = dir.resolve("pubspec.yaml")
            if (Files.isRegularFile(candidate)) return candidate
            if (stop != null && dir == stop) break
            dir = dir.parent
        }
        return null
    }

    fun flutterRootFor(file: Path, workspaceRoot: Path?): Path? {
        val pubspec = findPubspec(file, workspaceRoot) ?: return null
        val content = runCatching { Files.readString(pubspec) }.getOrNull() ?: return null
        if (!isFlutterPubspec(content)) return null
        return pubspec.parent
    }
}
