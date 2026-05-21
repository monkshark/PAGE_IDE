package page.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleasesParseTest {

    @Test
    fun parseReleasesPicksTagAndPrereleaseFlag() {
        val json = """
            [
              {"tag_name":"v1.2.3","published_at":"2025-04-01T00:00:00Z","prerelease":false},
              {"tag_name":"v1.2.4-beta","published_at":"2025-04-02T00:00:00Z","prerelease":true}
            ]
        """.trimIndent()
        val releases = GitHubReleases.parseReleases(json)
        assertEquals(2, releases.size)
        assertEquals("v1.2.3", releases[0].tagName)
        assertEquals(false, releases[0].prerelease)
        assertEquals("v1.2.4-beta", releases[1].tagName)
        assertEquals(true, releases[1].prerelease)
        assertEquals("2025-04-01T00:00:00Z", releases[0].publishedAt)
    }

    @Test
    fun parseReleasesIsEmptyForEmptyArray() {
        assertEquals(0, GitHubReleases.parseReleases("[]").size)
    }

    @Test
    fun parseReleasesUnescapesQuotes() {
        val json = """[{"tag_name":"v\"weird\"","prerelease":false}]"""
        val releases = GitHubReleases.parseReleases(json)
        assertEquals(1, releases.size)
        assertEquals("v\"weird\"", releases[0].tagName)
    }

    @Test
    fun findAssetDownloadUrlMatchesSuffix() {
        val json = """
            {
              "assets":[
                {"name":"jdtls-windows-x64.zip","browser_download_url":"https://example.com/jdtls.zip"},
                {"name":"jdtls.tar.gz","browser_download_url":"https://example.com/jdtls.tar.gz"}
              ]
            }
        """.trimIndent()
        assertEquals(
            "https://example.com/jdtls.tar.gz",
            GitHubReleases.findAssetDownloadUrl(json, ".tar.gz"),
        )
        assertEquals(
            "https://example.com/jdtls.zip",
            GitHubReleases.findAssetDownloadUrl(json, ".zip"),
        )
    }

    @Test
    fun findAssetDownloadUrlReturnsNullWhenMissing() {
        val json = """{"assets":[{"browser_download_url":"https://example.com/asset.dmg"}]}"""
        assertNull(GitHubReleases.findAssetDownloadUrl(json, ".tar.xz"))
    }

    @Test
    fun findAssetDownloadUrlIgnoresCaseOnSuffix() {
        val json = """{"assets":[{"browser_download_url":"https://example.com/asset.TAR.GZ"}]}"""
        assertNotNull(GitHubReleases.findAssetDownloadUrl(json, ".tar.gz"))
    }
}
