package page.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildCacheTest {

    private val temps = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        for (p in temps.reversed()) runCatching { Files.deleteIfExists(p) }
    }

    private fun temp(suffix: String): Path =
        Files.createTempFile("buildcache-", suffix).also { temps.add(it) }

    private fun setMtime(p: Path, millis: Long) {
        Files.setLastModifiedTime(p, FileTime.fromMillis(millis))
    }

    @Test
    fun upToDateWhenOutputNewerAndKeyMatches() {
        val input = temp(".swift")
        val output = temp(".exe")
        setMtime(input, 1_000)
        setMtime(output, 2_000)
        BuildCache.record(output, "swiftc x -o out")
        temps.add(output.resolveSibling("${output.fileName}.pagebuild"))
        assertTrue(BuildCache.upToDate(output, listOf(input), "swiftc x -o out"))
    }

    @Test
    fun staleWhenInputNewerThanOutput() {
        val input = temp(".swift")
        val output = temp(".exe")
        setMtime(output, 1_000)
        setMtime(input, 2_000)
        BuildCache.record(output, "k")
        temps.add(output.resolveSibling("${output.fileName}.pagebuild"))
        assertFalse(BuildCache.upToDate(output, listOf(input), "k"))
    }

    @Test
    fun staleWhenBuildKeyDiffers() {
        val input = temp(".swift")
        val output = temp(".exe")
        setMtime(input, 1_000)
        setMtime(output, 2_000)
        BuildCache.record(output, "old flags")
        temps.add(output.resolveSibling("${output.fileName}.pagebuild"))
        assertFalse(BuildCache.upToDate(output, listOf(input), "new flags"))
    }

    @Test
    fun staleWhenNoMarkerRecorded() {
        val input = temp(".swift")
        val output = temp(".exe")
        setMtime(input, 1_000)
        setMtime(output, 2_000)
        assertFalse(BuildCache.upToDate(output, listOf(input), "k"))
    }

    @Test
    fun staleWhenOutputMissing() {
        val input = temp(".swift")
        val output = Path.of(System.getProperty("java.io.tmpdir"), "buildcache-missing-${input.fileName}.exe")
        assertFalse(BuildCache.upToDate(output, listOf(input), "k"))
    }

    @Test
    fun staleWhenNoInputs() {
        val output = temp(".exe")
        BuildCache.record(output, "k")
        temps.add(output.resolveSibling("${output.fileName}.pagebuild"))
        assertFalse(BuildCache.upToDate(output, emptyList(), "k"))
    }
}
