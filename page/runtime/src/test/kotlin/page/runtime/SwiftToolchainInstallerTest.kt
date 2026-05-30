package page.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwiftToolchainInstallerTest {

    private fun windows() = SwiftToolchainInstaller(
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
    )

    @Test
    fun windowsDownloadUrlUsesX8664TarGz() {
        val url = windows().downloadUrl("6.0.3")
        assertEquals(
            "https://github.com/monkshark/page-ide-assets/releases/download/" +
                "swift-toolchain-bundle/page-swift-toolchain-windows-x86_64-6.0.3.tar.gz",
            url,
        )
    }

    @Test
    fun windowsBinariesAreExeUnderUsrBin() {
        val w = windows()
        val lsp = w.sourcekitLspBinary("6.0.3").toString().replace('\\', '/')
        val swift = w.swiftBinary("6.0.3").toString().replace('\\', '/')
        assertTrue(lsp.endsWith("/swift/6.0.3/usr/bin/sourcekit-lsp.exe"), lsp)
        assertTrue(swift.endsWith("/swift/6.0.3/usr/bin/swift.exe"), swift)
    }

    @Test
    fun sdkRootSitsBesideUsrUnderVersionDir() {
        val sdk = windows().sdkRootFor("6.0.3").toString().replace('\\', '/')
        assertTrue(sdk.endsWith("/swift/6.0.3/sdk"), sdk)
    }

    @Test
    fun unixBinariesHaveNoExeSuffix() {
        val u = SwiftToolchainInstaller(osKey = "linux", archKey = "amd64", isWindows = false)
        val swift = u.swiftBinary("6.0.3").toString().replace('\\', '/')
        assertTrue(swift.endsWith("/swift/6.0.3/usr/bin/swift"), swift)
    }
}
