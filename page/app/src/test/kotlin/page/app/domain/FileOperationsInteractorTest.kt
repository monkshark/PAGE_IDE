package page.app.domain

import kotlinx.coroutines.runBlocking
import page.workspace.ReplaceRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileOperationsInteractorTest {
    private fun request(targets: List<Path>, query: String, replacement: String) = ReplaceRequest(
        query = query,
        replacement = replacement,
        caseSensitive = true,
        regex = false,
        wholeWord = false,
        targets = targets,
    )

    @Test
    fun replacesAcrossMultipleFilesAndReportsCounts() = runBlocking {
        val a = Path.of("a.txt")
        val b = Path.of("b.txt")
        val store = mutableMapOf(
            a to "foo bar foo",
            b to "foo only",
        )
        val interactor = FileOperationsInteractor(
            readFileText = { store[it] },
            applyTextReplace = { path, text -> store[path] = text },
        )

        val result = interactor.replaceInFiles(request(listOf(a, b), "foo", "baz"))

        assertEquals(2, result.filesChanged)
        assertEquals(3, result.replacements)
        assertEquals("baz bar baz", store[a])
        assertEquals("baz only", store[b])
        assertEquals(setOf(a, b), result.updates.keys)
    }

    @Test
    fun skipsUnreadableAndUnchangedFiles() = runBlocking {
        val missing = Path.of("missing.txt")
        val noMatch = Path.of("nomatch.txt")
        val store = mutableMapOf(noMatch to "nothing here")
        val interactor = FileOperationsInteractor(
            readFileText = { store[it] },
            applyTextReplace = { path, text -> store[path] = text },
        )

        val result = interactor.replaceInFiles(request(listOf(missing, noMatch), "foo", "baz"))

        assertEquals(0, result.filesChanged)
        assertEquals(0, result.replacements)
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun ioExceptionOnWriteSkipsThatFileOnly() = runBlocking {
        val good = Path.of("good.txt")
        val locked = Path.of("locked.txt")
        val store = mutableMapOf(
            good to "foo",
            locked to "foo",
        )
        val interactor = FileOperationsInteractor(
            readFileText = { store[it] },
            applyTextReplace = { path, text ->
                if (path == locked) throw java.io.IOException("locked")
                store[path] = text
            },
        )

        val result = interactor.replaceInFiles(request(listOf(good, locked), "foo", "baz"))

        assertEquals(1, result.filesChanged)
        assertEquals(1, result.replacements)
        assertEquals("baz", store[good])
        assertTrue(result.updates.containsKey(good))
        assertFalse(result.updates.containsKey(locked))
    }
}
