package page.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createDirectories
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

class RubyBootstrapInstallerTest {

    private var savedHome: String? = null
    private lateinit var tempHome: Path

    private fun useTempHome(): Path {
        savedHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("page-ruby-test-home-")
        System.setProperty("user.home", tempHome.toString())
        return tempHome
    }

    @AfterTest
    fun restoreHome() {
        savedHome?.let { System.setProperty("user.home", it) }
        savedHome = null
        if (::tempHome.isInitialized) {
            runCatching {
                Files.walk(tempHome).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun writeFakeWindowsInstaller(target: Path) {
        Files.writeString(target, "fake rubyinstaller-devkit.exe")
    }

    private fun writeFakeMacTarGz(target: Path) {
        GZIPOutputStream(Files.newOutputStream(target)).use { gz ->
            TarArchiveOutputStream(gz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
                listOf(
                    "portable-ruby/3.3.7/bin/ruby" to "fake ruby",
                    "portable-ruby/3.3.7/bin/gem" to "fake gem",
                ).forEach { (name, body) ->
                    val entry = TarArchiveEntry(name)
                    val bytes = body.toByteArray()
                    entry.size = bytes.size.toLong()
                    entry.mode = "0755".toInt(8)
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
            }
        }
    }

    private fun winInstaller(
        processRunner: ProcessRunner = neverCalledRunner(),
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { url, target, onProgress ->
            if (url.endsWith(".zip")) {
                Files.writeString(target, "fake msys2 bundle zip")
            } else {
                writeFakeWindowsInstaller(target)
            }
            onProgress(100, 100)
        },
        zipExtractor: (Path, Path, Int) -> Unit = { _, dst, _ ->
            val ucrtBin = dst.resolve("ucrt64").resolve("bin")
            Files.createDirectories(ucrtBin)
            Files.writeString(ucrtBin.resolve("gcc.exe"), "fake gcc")
        },
    ): RubyBootstrapInstaller = RubyBootstrapInstaller(
        processRunner = processRunner,
        osKey = "windows",
        archKey = "amd64",
        isWindows = true,
        downloader = downloader,
        zipExtractor = zipExtractor,
    )

    private fun macInstaller(
        processRunner: ProcessRunner = neverCalledRunner(),
        archKey: String = "arm64",
        downloader: (String, Path, (Long, Long) -> Unit) -> Unit = { _, target, onProgress ->
            writeFakeMacTarGz(target)
            onProgress(100, 100)
        },
    ): RubyBootstrapInstaller = RubyBootstrapInstaller(
        processRunner = processRunner,
        osKey = "macos",
        archKey = archKey,
        isWindows = false,
        downloader = downloader,
    )

    private fun neverCalledRunner(): ProcessRunner = object : ProcessRunner {
        override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = fail("processRunner should not be called (no-env overload)")
        override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = fail("processRunner should not be called (env overload)")
        override fun captureOutput(command: List<String>): String = fail("captureOutput should not be called")
    }

    private fun isUacCommand(command: List<String>): Boolean =
        command.firstOrNull()?.equals("powershell.exe", ignoreCase = true) == true &&
            command.joinToString(" ").let { joined ->
                joined.contains("Start-Process") && joined.contains("-Verb RunAs")
            }

    @Test
    fun heavyInstallNonNull() {
        val installer = winInstaller()
        val heavy = installer.heavyInstall
        assertNotNull(heavy)
        assertTrue(heavy.sizeEstimate.isNotBlank())
        assertTrue(heavy.durationEstimate.isNotBlank())
        assertTrue(heavy.notes.isNotBlank())
    }

    @Test
    fun precheckIsOk() {
        assertTrue(winInstaller().precheck is LspInstaller.Precheck.Ok)
        assertTrue(macInstaller().precheck is LspInstaller.Precheck.Ok)
    }

    @Test
    fun downloadUrlForWindowsX64() {
        val installer = RubyBootstrapInstaller(osKey = "windows", archKey = "amd64", isWindows = true)
        assertEquals(
            "https://github.com/oneclick/rubyinstaller2/releases/download/RubyInstaller-3.4.6-1/rubyinstaller-devkit-3.4.6-1-x64.exe",
            installer.downloadUrl("3.4.6"),
        )
    }

    @Test
    fun downloadUrlForWindowsArm() {
        val installer = RubyBootstrapInstaller(osKey = "windows", archKey = "arm64", isWindows = true)
        val url = installer.downloadUrl("3.4.6")
        assertTrue(url.endsWith("-arm.exe"), "Windows ARM uses '-arm.exe' suffix (InnoSetup installer): $url")
        assertTrue(url.contains("rubyinstaller-devkit-"), "Windows uses devkit-included installer, got: $url")
    }

    @Test
    fun downloadUrlForMacosArm64() {
        val installer = RubyBootstrapInstaller(osKey = "macos", archKey = "arm64", isWindows = false)
        assertEquals(
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/3.4.6/portable-ruby-3.4.6.arm64_big_sur.bottle.tar.gz",
            installer.downloadUrl("3.4.6"),
        )
    }

    @Test
    fun downloadUrlForMacosX86() {
        val installer = RubyBootstrapInstaller(osKey = "macos", archKey = "amd64", isWindows = false)
        assertEquals(
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/3.4.6/portable-ruby-3.4.6.el_capitan.bottle.tar.gz",
            installer.downloadUrl("3.4.6"),
        )
    }

    @Test
    fun executableNullBeforeInstall() {
        useTempHome()
        assertNull(winInstaller().executable())
        assertNull(macInstaller().executable())
    }

    @Test
    fun availableVersionsReturnsDefault() {
        val installer = winInstaller()
        assertEquals(listOf(RubyBootstrapInstaller.DEFAULT_RUBY_VERSION), installer.availableVersions())
    }

    @Test
    fun installEndToEndOnWindowsWritesSolargraphAndPointer() {
        useTempHome()
        val noEnvCommands = mutableListOf<List<String>>()
        val gemCommands = mutableListOf<List<String>>()
        val gemEnvs = mutableListOf<Map<String, String>>()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    noEnvCommands += command
                    val joined = command.joinToString(" ")
                    return when {
                        isUacCommand(command) -> {
                            onLine("added")
                            onLine("uac-ok")
                            0
                        }
                        command.first().endsWith(".exe") && command.contains("/VERYSILENT") -> {
                            val dirArg = command.first { it.startsWith("/DIR=") }
                            val target = Path.of(dirArg.removePrefix("/DIR="))
                            val bin = target.resolve("bin")
                            Files.createDirectories(bin)
                            Files.writeString(bin.resolve("ruby.exe"), "fake ruby")
                            Files.writeString(bin.resolve("gem.cmd"), "@ruby gem")
                            onLine("Installing Ruby 3.3.7 (silent)")
                            0
                        }
                        else -> fail("unexpected no-env command: $command")
                    }
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                    gemCommands += command
                    gemEnvs += env
                    onLine("Fetching solargraph-0.55.4.gem")
                    onLine("Successfully installed solargraph-0.55.4")
                    val gemHome = Path.of(env["GEM_HOME"]!!)
                    val solargraph = gemHome.resolve("bin").resolve("solargraph.bat")
                    solargraph.parent.createDirectories()
                    Files.writeString(solargraph, "@ruby solargraph shim")
                    return 0
                }
                override fun captureOutput(command: List<String>): String = ""
            },
        )

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        val installedExe = installer.executable()
        assertNotNull(installedExe)
        assertTrue(installer.isInstalled())
        assertEquals("3.3.7", installer.installedVersion())

        assertEquals(
            2,
            noEnvCommands.size,
            "expected UAC Defender exclusion + silent install (2 no-env invocations — MSYS2 bundle is downloaded+unzipped, not invoked as a process): $noEnvCommands",
        )
        val uac = noEnvCommands[0]
        assertEquals("powershell.exe", uac[0], "elevation must invoke powershell.exe directly so Start-Process -Verb RunAs can request UAC: $uac")
        val uacJoined = uac.joinToString(" ")
        assertTrue(uacJoined.contains("Start-Process"), "elevation must use Start-Process: $uac")
        assertTrue(uacJoined.contains("-Verb RunAs"), "elevation must request UAC via -Verb RunAs: $uac")
        assertTrue(uacJoined.contains("Add-MpPreference"), "inner ArgumentList must call Add-MpPreference: $uac")
        assertTrue(uacJoined.contains("ExclusionPath"), "inner ArgumentList must reference ExclusionPath: $uac")
        assertTrue(
            uacJoined.contains(installer.rubyRoot("3.3.7").toString()),
            "elevation script must embed the target install path literal: $uac",
        )

        val silent = noEnvCommands[1]
        assertTrue(silent.first().endsWith(".exe"), "second no-env call must be the .exe silent install: $silent")
        assertTrue(silent.contains("/VERYSILENT"), "silent install must pass /VERYSILENT: $silent")
        assertTrue(silent.contains("/CURRENTUSER"), "silent install must pass /CURRENTUSER: $silent")
        assertTrue(
            silent.any { it.startsWith("/DIR=") && it.endsWith(installer.rubyRoot("3.3.7").toString()) },
            "silent install must target rubyRoot via /DIR=: $silent",
        )

        val msys2BundleUrl = installer.msys2BundleUrl()
        assertTrue(
            msys2BundleUrl.startsWith("https://github.com/"),
            "msys2 bundle must be served from a GitHub releases URL so it bypasses the local AV/ASR path: $msys2BundleUrl",
        )
        assertTrue(
            msys2BundleUrl.endsWith(".zip"),
            "msys2 bundle must be a zip the bootstrap can extract without invoking bash.exe: $msys2BundleUrl",
        )
        val msys64Gcc = installer.rubyRoot("3.3.7")
            .resolve("msys64").resolve("ucrt64").resolve("bin").resolve("gcc.exe")
        assertTrue(
            Files.exists(msys64Gcc),
            "zipExtractor must materialise gcc.exe at <rubyRoot>/msys64/ucrt64/bin/gcc.exe (flatten=1 layout): $msys64Gcc",
        )

        assertEquals(2, gemCommands.size, "expected 2 gem invocations (rbs prism-free pin + solargraph): $gemCommands")

        val rbsCmd = gemCommands[0]
        assertEquals("rbs", rbsCmd.last(), "first gem invocation must be the prism-free rbs pin: $rbsCmd")
        val rbsVersionIdx = rbsCmd.indexOf("--version")
        assertTrue(rbsVersionIdx >= 0, "rbs pin must specify --version: $rbsCmd")
        assertEquals(
            RubyBootstrapInstaller.PRISM_FREE_RBS_VERSION,
            rbsCmd[rbsVersionIdx + 1],
            "rbs pin must equal PRISM_FREE_RBS_VERSION so transitive prism native ext is avoided",
        )

        val gemCmd = gemCommands[1]
        assertTrue(gemCmd.contains("install"))
        assertTrue(gemCmd.contains("--no-document"))
        assertTrue(gemCmd.contains("--conservative"), "solargraph install must be --conservative so the pinned rbs is kept: $gemCmd")
        assertTrue(gemCmd.contains("solargraph"))
        assertEquals("solargraph", gemCmd.last())
        val versionIdx = gemCmd.indexOf("--version")
        assertTrue(versionIdx >= 0, "must pin solargraph version to avoid prism native ext on Windows: $gemCmd")
        assertEquals(
            RubyBootstrapInstaller.DEFAULT_SOLARGRAPH_VERSION,
            gemCmd[versionIdx + 1],
            "pinned solargraph version must equal DEFAULT_SOLARGRAPH_VERSION (0.55.x = last prism-free release)",
        )
        assertEquals("cmd.exe", gemCmd[0], "Windows install must go through cmd.exe so the .cmd shim parses correctly: $gemCmd")
        assertEquals("/c", gemCmd[1])
        assertTrue(
            gemCmd[2].endsWith("gem.cmd"),
            "Windows must invoke gem.cmd directly so our env (MSYSTEM/MINGW_PREFIX/RI_DEVKIT/PATH) drives MSYS2 — ridk's shift logic breaks 'exec gem' chaining: $gemCmd",
        )
        assertEquals("install", gemCmd[3])

        val env = gemEnvs.last()
        assertNotNull(env["GEM_HOME"])
        assertNotNull(env["GEM_PATH"])
        assertNotNull(env["PATH"])
        val rubyRoot = installer.rubyRoot("3.3.7")
        val msys2Ucrt = rubyRoot.resolve("msys64").resolve("ucrt64").resolve("bin").toString()
        assertTrue(env["PATH"]!!.contains(rubyRoot.resolve("bin").toString()))
        assertTrue(
            env["PATH"]!!.contains(msys2Ucrt),
            "PATH must include MSYS2 ucrt64/bin so mkmf gcc resolves: ${env["PATH"]}",
        )
        assertTrue(
            env["PATH"]!!.indexOf(msys2Ucrt) < env["PATH"]!!.indexOf(rubyRoot.resolve("bin").toString()),
            "MSYS2 ucrt64/bin must come BEFORE ruby/bin so mkmf PATH revert keeps gcc reachable",
        )
        assertEquals("UCRT64", env["MSYSTEM"], "Ruby RbConfig needs MSYSTEM=UCRT64 to resolve mingw toolchain")
        assertEquals("/ucrt64", env["MINGW_PREFIX"])
        assertEquals("x86_64", env["MSYSTEM_CARCH"])
        assertEquals(rubyRoot.resolve("msys64").toString(), env["RI_DEVKIT"])
    }

    @Test
    fun installFailsOnWindowsWhenSilentInstallExitsNonZero() {
        useTempHome()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    if (isUacCommand(command)) return 0
                    return 5
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = fail("env overload must not be reached when silent install fails")
                override fun captureOutput(command: List<String>): String = ""
            },
        )
        var failed: Throwable? = null
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.Failed) failed = p.error }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("silent install"))
    }

    @Test
    fun installFailsWhenMsys2BundleZipDoesNotProduceGcc() {
        useTempHome()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    if (isUacCommand(command)) return 0
                    if (command.first().endsWith(".exe") && command.contains("/VERYSILENT")) {
                        val target = Path.of(command.first { it.startsWith("/DIR=") }.removePrefix("/DIR="))
                        val bin = target.resolve("bin")
                        Files.createDirectories(bin)
                        Files.writeString(bin.resolve("ruby.exe"), "fake ruby")
                        Files.writeString(bin.resolve("gem.cmd"), "@ruby gem")
                        return 0
                    }
                    return fail("unexpected no-env command: $command")
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = fail("env overload must not be reached when gcc verification fails")
                override fun captureOutput(command: List<String>): String = ""
            },
            zipExtractor = { _, _, _ -> /* zip exists but extracted layout is wrong — gcc.exe never appears */ },
        )
        var failed: Throwable? = null
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.Failed) failed = p.error }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("gcc"), "expected gcc-missing diagnostic: ${failed!!.message}")
    }

    @Test
    fun installFailsWhenMsys2BundleDownloadThrows() {
        useTempHome()
        val installer = winInstaller(
            processRunner = object : ProcessRunner {
                override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int {
                    if (isUacCommand(command)) return 0
                    if (command.first().endsWith(".exe") && command.contains("/VERYSILENT")) {
                        val target = Path.of(command.first { it.startsWith("/DIR=") }.removePrefix("/DIR="))
                        val bin = target.resolve("bin")
                        Files.createDirectories(bin)
                        Files.writeString(bin.resolve("ruby.exe"), "fake ruby")
                        Files.writeString(bin.resolve("gem.cmd"), "@ruby gem")
                        return 0
                    }
                    return fail("unexpected no-env command: $command")
                }
                override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = fail("env overload must not be reached when bundle download fails")
                override fun captureOutput(command: List<String>): String = ""
            },
            downloader = { url, target, onProgress ->
                if (url.endsWith(".zip")) {
                    throw java.io.IOException("simulated network failure")
                } else {
                    writeFakeWindowsInstaller(target)
                    onProgress(100, 100)
                }
            },
        )
        var failed: Throwable? = null
        installer.install("3.3.7") { p -> if (p is LspInstaller.Progress.Failed) failed = p.error }
        assertNotNull(failed)
        val msg = failed!!.message!!
        assertTrue(msg.contains("MSYS2"), "diagnostic must mention MSYS2 bundle: $msg")
        assertTrue(msg.contains(installer.msys2BundleUrl()), "diagnostic must echo the bundle URL: $msg")
    }

    @Test
    fun installEndToEndOnMacosWritesSolargraphAndPointer() {
        useTempHome()
        val executedCommands = mutableListOf<List<String>>()
        val runner = object : ProcessRunner {
            override fun runStreaming(command: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(command: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int {
                executedCommands += command
                onLine("Successfully installed solargraph-0.50.0")
                val gemHome = Path.of(env["GEM_HOME"]!!)
                val solargraph = gemHome.resolve("bin").resolve("solargraph")
                solargraph.parent.createDirectories()
                Files.writeString(solargraph, "#!/usr/bin/env ruby")
                return 0
            }
            override fun captureOutput(command: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)

        var lastEvent: LspInstaller.Progress? = null
        installer.install("3.3.7") { lastEvent = it }

        assertTrue(lastEvent is LspInstaller.Progress.Done, "expected Done, got $lastEvent")
        assertEquals("3.3.7", installer.installedVersion())
        assertEquals(2, executedCommands.size, "expected rbs pin + solargraph: $executedCommands")
        assertEquals("rbs", executedCommands[0].last(), "first gem invocation must pin rbs (prism-free)")
        val cmd = executedCommands[1]
        assertEquals("gem", Path.of(cmd[0]).fileName.toString(), "macOS install should invoke gem directly: $cmd")
        assertTrue(cmd.contains("--conservative"), "solargraph install must be --conservative: $cmd")
        assertTrue(cmd.last() == "solargraph")
    }

    @Test
    fun installFailsWhenSolargraphBinaryMissingAfterRun() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 0
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
    }

    @Test
    fun installFailsWhenGemExitNonZero() {
        useTempHome()
        val runner = object : ProcessRunner {
            override fun runStreaming(c: List<String>, onLine: (String) -> Unit): Int = fail("env-aware overload expected")
            override fun runStreaming(c: List<String>, env: Map<String, String>, onLine: (String) -> Unit): Int = 7
            override fun captureOutput(c: List<String>): String = ""
        }
        val installer = macInstaller(processRunner = runner)
        var failed: Throwable? = null
        installer.install("3.3.7") { p ->
            if (p is LspInstaller.Progress.Failed) failed = p.error
        }
        assertNotNull(failed)
        assertTrue(failed!!.message!!.contains("7"))
    }
}
