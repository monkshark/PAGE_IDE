package page.runtime

import java.nio.file.Files
import java.nio.file.Path

object BuildCache {

    fun upToDate(output: Path, inputs: List<Path>, buildKey: String): Boolean {
        if (inputs.isEmpty() || !Files.exists(output)) return false
        if (inputs.any { !Files.exists(it) }) return false
        val outTime = Files.getLastModifiedTime(output).toMillis()
        if (inputs.any { Files.getLastModifiedTime(it).toMillis() > outTime }) return false
        val recorded = runCatching { Files.readString(markerFor(output)) }.getOrNull()
        return recorded == buildKey
    }

    fun record(output: Path, buildKey: String) {
        runCatching { Files.writeString(markerFor(output), buildKey) }
    }

    private fun markerFor(output: Path): Path =
        output.resolveSibling("${output.fileName}.pagebuild")
}
