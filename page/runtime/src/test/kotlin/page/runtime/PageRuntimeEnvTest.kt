package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageRuntimeEnvTest {

    @Test
    fun applyToPrependsPathAndSetsEnvVars() {
        val env = mutableMapOf("PATH" to "/usr/bin")
        PageRuntimeEnv.applyTo(env)
        val path = env["PATH"] ?: ""
        assertTrue(path.endsWith("/usr/bin"), "original PATH should be preserved at end")
    }

    @Test
    fun applyToHandlesEmptyPath() {
        val env = mutableMapOf<String, String>()
        PageRuntimeEnv.applyTo(env)
        val runtimes = PageRuntimeEnv.collectRuntimes()
        if (runtimes.isNotEmpty()) {
            assertTrue(env.containsKey("PATH"), "PATH should be created if runtimes exist")
        }
    }

    @Test
    fun applyToIsCaseInsensitiveForPathKey() {
        val env = mutableMapOf("Path" to "/usr/bin")
        PageRuntimeEnv.applyTo(env)
        assertTrue(env.containsKey("Path"), "original casing should be preserved")
    }

    @Test
    fun collectRuntimesDoesNotThrow() {
        val runtimes = PageRuntimeEnv.collectRuntimes()
        assertTrue(runtimes is List)
    }

    @Test
    fun collapseMergesCaseInsensitiveDuplicatePathKeys() {
        val env = linkedMapOf(
            "Path" to "C:\\base",
            "PATH" to "C:\\swift\\bin;C:\\base;C:\\extra",
            "SDKROOT" to "C:\\sdk",
        )
        PageRuntimeEnv.collapseCaseInsensitiveDuplicates(env)
        val pathKeys = env.keys.filter { it.equals("Path", ignoreCase = true) }
        assertEquals(1, pathKeys.size, "only one case-insensitive Path key may survive")
        assertEquals("C:\\swift\\bin;C:\\base;C:\\extra", env[pathKeys.single()], "fullest value wins")
        assertEquals("C:\\sdk", env["SDKROOT"], "unrelated keys untouched")
    }

    @Test
    fun collapseLeavesSingleKeyUntouched() {
        val env = linkedMapOf("PATH" to "C:\\bin", "JAVA_HOME" to "C:\\jdk")
        PageRuntimeEnv.collapseCaseInsensitiveDuplicates(env)
        assertEquals("C:\\bin", env["PATH"])
        assertEquals("C:\\jdk", env["JAVA_HOME"])
        assertEquals(2, env.size)
    }

    @Test
    fun collapseHandlesThreeWayCollision() {
        val env = linkedMapOf("path" to "a", "Path" to "aa", "PATH" to "aaa")
        PageRuntimeEnv.collapseCaseInsensitiveDuplicates(env)
        assertEquals(1, env.size)
        assertEquals("aaa", env.values.single())
        assertTrue(env.keys.single().equals("path", ignoreCase = true))
    }
}
