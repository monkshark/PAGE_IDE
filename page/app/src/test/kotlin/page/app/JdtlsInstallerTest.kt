package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdtlsInstallerTest {

    @Test
    fun parseMilestoneVersionsExtractsSemverDirs() {
        val html = """
            <html><body>
            <a href="../">Parent Directory</a>
            <a href="1.59.0/">1.59.0/</a>
            <a href="1.58.0/">1.58.0/</a>
            <a href="1.43.1/">1.43.1/</a>
            <a href="latest.txt">latest.txt</a>
            <a href="some-other-link/">some-other-link/</a>
            </body></html>
        """.trimIndent()
        val out = JdtlsInstaller.parseMilestoneVersions(html)
        assertEquals(listOf("1.59.0", "1.58.0", "1.43.1"), out)
    }

    @Test
    fun parseMilestoneVersionsDedupesAndSorts() {
        val html = """
            <a href="1.43.1/">x</a>
            <a href="1.43.1/">y</a>
            <a href="1.43.0/">z</a>
            <a href="1.59.0/">a</a>
        """.trimIndent()
        assertEquals(listOf("1.59.0", "1.43.1", "1.43.0"), JdtlsInstaller.parseMilestoneVersions(html))
    }

    @Test
    fun versionDescOrdersHighestFirst() {
        val mixed = listOf("1.43.0", "1.59.0", "1.58.10", "1.58.2", "2.0.0")
        val sorted = mixed.sortedWith(JdtlsInstaller.VERSION_DESC)
        assertEquals(listOf("2.0.0", "1.59.0", "1.58.10", "1.58.2", "1.43.0"), sorted)
    }

    @Test
    fun availableVersionsPrependsSnapshotLatest() {
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> listOf("1.59.0", "1.58.0") },
            snapshotFileFetcher = { _ -> "jdt-language-server-latest.tar.gz" },
            milestoneFileFetcher = { _, v -> "jdt-language-server-$v-202605111959.tar.gz" },
        )
        assertEquals(listOf("snapshot-latest", "1.59.0", "1.58.0"), installer.availableVersions())
    }

    @Test
    fun availableVersionsFallsBackToSnapshotOnlyOnFetcherFailure() {
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> throw IOException("boom") },
        )
        assertEquals(listOf("snapshot-latest"), installer.availableVersions())
    }

    @Test
    fun installComposesMilestoneDownloadUrl() {
        val urls = mutableListOf<String>()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> listOf("1.59.0") },
            snapshotFileFetcher = { _ -> "snap.tar.gz" },
            milestoneFileFetcher = { _, v -> "jdt-language-server-$v-202605111959.tar.gz" },
            downloader = { url, _, _ ->
                urls += url
                throw IOException("stop here — URL capture only")
            },
        )
        installer.install("1.59.0") { }
        assertEquals(1, urls.size)
        assertEquals(
            "https://example.com/jdtls/milestones/1.59.0/jdt-language-server-1.59.0-202605111959.tar.gz",
            urls.single(),
        )
    }

    @Test
    fun installComposesSnapshotDownloadUrlForSnapshotLatest() {
        val urls = mutableListOf<String>()
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> emptyList() },
            snapshotFileFetcher = { _ -> "jdt-language-server-latest.tar.gz" },
            milestoneFileFetcher = { _, _ -> null },
            downloader = { url, _, _ ->
                urls += url
                throw IOException("stop here — URL capture only")
            },
        )
        installer.install("snapshot-latest") { }
        assertEquals(1, urls.size)
        assertEquals(
            "https://example.com/jdtls/snapshots/jdt-language-server-latest.tar.gz",
            urls.single(),
        )
    }

    @Test
    fun installEmitsFailedWhenSnapshotFileMissing() {
        val installer = JdtlsInstaller(
            baseUrl = "https://example.com/jdtls",
            versionsFetcher = { _ -> emptyList() },
            snapshotFileFetcher = { _ -> null },
            milestoneFileFetcher = { _, _ -> null },
        )
        var failed: Throwable? = null
        installer.install("snapshot-latest") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("파일명 조회 실패"))
    }
}
