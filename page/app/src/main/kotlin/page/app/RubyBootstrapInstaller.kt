package page.app

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class RubyBootstrapInstaller(
    private val processRunner: ProcessRunner = DefaultProcessRunner,
    private val osKey: String = LspInstaller.osKey(),
    private val archKey: String = ArchDetect.archKey(),
    private val isWindows: Boolean = LspInstaller.isWindows(),
    private val downloader: (url: String, target: Path, onProgress: (Long, Long) -> Unit) -> Unit = InstallerHttp::download,
    private val tarGzExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractTarGz(src, dst, flatten) },
    private val zipExtractor: (Path, Path, Int) -> Unit = { src, dst, flatten -> ArchiveExtractors.extractZip(src, dst, flatten) },
    private val defaultRubyVersion: String = DEFAULT_RUBY_VERSION,
    private val rubyInstallerRelease: String = DEFAULT_RUBYINSTALLER_RELEASE,
    private val macBottleSlug: String = DEFAULT_MAC_BOTTLE_SLUG,
    private val solargraphPackage: String = "solargraph",
    private val solargraphVersion: String = DEFAULT_SOLARGRAPH_VERSION,
    private val msys2BundleRelease: String = DEFAULT_MSYS2_BUNDLE_RELEASE,
    private val msys2BundleRepo: String = DEFAULT_MSYS2_BUNDLE_REPO,
) : LspInstaller {

    override val languageId: String = "ruby"
    override val displayName: String = "solargraph"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate? = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = if (isWindows) "약 150 MB Ruby 인스톨러 + 약 700 MB MSYS2/MinGW 번들" else "약 25 MB",
        durationEstimate = "약 3분 ~ 7분",
        notes = if (isWindows)
            "PAGE 가 Ruby + DevKit 인스톨러를 silent install 한 뒤, PAGE 가 미리 빌드해 둔 MSYS2 + MinGW UCRT64 zip 번들을 받아 격리 디렉터리에 풀고 gem 으로 solargraph 를 설치합니다."
        else
            "PAGE 가 Ruby 런타임을 받아 풀고 그 안의 gem 으로 solargraph 를 설치합니다.",
    )

    override fun isInstalled(): Boolean = executable() != null

    override fun executable(): Path? {
        val ver = currentInstalledVersion() ?: return null
        return findInstalledSolargraph(ver)
    }

    override fun defaultVersion(): String? = defaultRubyVersion

    override fun installedVersion(): String? = currentInstalledVersion()

    override fun availableVersions(): List<String> = listOf(defaultRubyVersion)

    override fun install(version: String?, rawProgress: (LspInstaller.Progress) -> Unit) {
        val logFile = LspInstaller.lspHome().resolve("ruby-bootstrap").resolve("install.log")
        runCatching {
            Files.createDirectories(logFile.parent)
            Files.writeString(logFile, "=== Ruby bootstrap install @ ${java.time.LocalDateTime.now()} ===\n")
        }
        val onProgress: (LspInstaller.Progress) -> Unit = { p ->
            rawProgress(p)
            when (p) {
                is LspInstaller.Progress.CommandOutput -> runCatching { Files.writeString(logFile, p.line + "\n", java.nio.file.StandardOpenOption.APPEND) }
                is LspInstaller.Progress.Failed -> runCatching { Files.writeString(logFile, "FAILED: ${p.error.javaClass.simpleName}: ${p.error.message}\n", java.nio.file.StandardOpenOption.APPEND) }
                is LspInstaller.Progress.Done -> runCatching { Files.writeString(logFile, "DONE: ${p.executable}\n", java.nio.file.StandardOpenOption.APPEND) }
                else -> Unit
            }
        }
        try {
            val resolved = version ?: defaultRubyVersion
            val url = downloadUrl(resolved)
            val target = rubyRoot(resolved)
            val ext = if (isWindows) ".exe" else ".tar.gz"
            val tmp = Files.createTempFile("page-ruby-", ext)
            try {
                downloader(url, tmp) { read, total ->
                    onProgress(LspInstaller.Progress.Downloading(read, total))
                }
                if (isWindows) {
                    Files.createDirectories(target)
                    requestDefenderExclusion(target, onProgress)
                    installWindowsRuntime(tmp, target, onProgress)
                } else {
                    onProgress(LspInstaller.Progress.Extracting("Extracting Ruby runtime…"))
                    tarGzExtractor(tmp, target, 2)
                }
            } finally {
                runCatching { Files.deleteIfExists(tmp) }
            }

            val gemBin = gemBinary(resolved)
            if (!Files.exists(gemBin)) throw IOException("Ruby 부트스트랩 후 gem 누락: $gemBin")
            runCatching { gemBin.toFile().setExecutable(true, false) }
            val rubyBin = rubyBinary(resolved)
            runCatching { rubyBin.toFile().setExecutable(true, false) }

            val gemHome = gemHomeFor(resolved)
            Files.createDirectories(gemHome)

            val env = buildInstallEnv(resolved, gemHome)
            onProgress(LspInstaller.Progress.CommandOutput("[debug] cwd       = ${System.getProperty("user.dir")}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] user.home = ${System.getProperty("user.home")}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] GEM_HOME  = ${env["GEM_HOME"]}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] GEM_PATH  = ${env["GEM_PATH"]}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] MSYSTEM   = ${env["MSYSTEM"]}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] RI_DEVKIT = ${env["RI_DEVKIT"]}"))
            onProgress(LspInstaller.Progress.CommandOutput("[debug] PATH(head)= ${env["PATH"]?.take(300)}…"))

            val rbsCmd = gemInvocation(gemBin, listOf("install", "--no-document", "--version", PRISM_FREE_RBS_VERSION, "rbs"))
            onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --version $PRISM_FREE_RBS_VERSION rbs (prism-free pin)"))
            val rbsExit = processRunner.runStreaming(rbsCmd, env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            onProgress(LspInstaller.Progress.CommandOutput("[debug] gem install rbs 종료 코드 = $rbsExit"))
            if (rbsExit != 0) throw IOException("gem install rbs 종료 코드 $rbsExit")

            val installCmd = gemInvocation(
                gemBin,
                listOf(
                    "install", "--no-document", "--conservative",
                    "--version", solargraphVersion,
                    solargraphPackage,
                ),
            )
            onProgress(LspInstaller.Progress.CommandOutput("[debug] cmd args = ${installCmd.joinToString(" | ")}"))
            onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --conservative --version $solargraphVersion $solargraphPackage"))
            val exit = processRunner.runStreaming(installCmd, env) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
            onProgress(LspInstaller.Progress.CommandOutput("[debug] gem install 종료 코드 = $exit"))
            if (exit != 0) throw IOException("gem install solargraph 종료 코드 $exit")

            val solargraph = findInstalledSolargraph(resolved)
                ?: throw IOException(
                    "solargraph 설치 후 바이너리 누락: ${gemHomeFor(resolved).resolve("bin")} 안에서 " +
                        solargraphCandidateNames().joinToString("/") + " 를 찾을 수 없습니다",
                )
            runCatching { solargraph.toFile().setExecutable(true, false) }

            writePointer(resolved)
            onProgress(LspInstaller.Progress.Done(solargraph))
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.Failed(t))
        }
    }

    private fun requestDefenderExclusion(target: Path, onProgress: (LspInstaller.Progress) -> Unit): Boolean {
        onProgress(LspInstaller.Progress.Extracting("Requesting Windows Defender exclusion (UAC prompt)…"))
        val pathLiteral = target.toString().replace("'", "''")
        val innerCommand = "try { Add-MpPreference -ExclusionPath ''" + pathLiteral +
            "''; exit 0 } catch { exit 2 }"
        val outerCommand = "Start-Process powershell -Verb RunAs -WindowStyle Hidden -Wait " +
            "-ArgumentList '-NoProfile -ExecutionPolicy Bypass -Command \"" + innerCommand + "\"'"
        val cmd = listOf(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-Command",
            outerCommand,
        )
        onProgress(LspInstaller.Progress.CommandOutput("> [UAC] $outerCommand"))
        val exit = try {
            processRunner.runStreaming(cmd) { line ->
                onProgress(LspInstaller.Progress.CommandOutput(line))
            }
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] UAC elevation failed to launch: ${t.javaClass.simpleName}: ${t.message}",
            ))
            return false
        }
        if (exit != 0) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] Defender exclusion request exited with code $exit (UAC denied or PowerShell launch failed). 설치는 계속 진행합니다.",
            ))
            return false
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] Defender exclusion 등록 시도 완료 (exit=0)"))
        return true
    }

    private fun installWindowsRuntime(installer: Path, target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        onProgress(LspInstaller.Progress.Extracting("Installing Ruby + DevKit (silent)…"))
        Files.createDirectories(target.parent)
        val cmd = listOf(
            installer.toString(),
            "/VERYSILENT",
            "/CURRENTUSER",
            "/SUPPRESSMSGBOXES",
            "/NORESTART",
            "/NOICONS",
            "/DIR=${target}",
            "/TASKS=!modpath,!assocfiles,defaultutf8",
        )
        onProgress(LspInstaller.Progress.CommandOutput("> ${installer.fileName} /VERYSILENT /DIR=$target"))
        val exit = processRunner.runStreaming(cmd) { line ->
            onProgress(LspInstaller.Progress.CommandOutput(line))
        }
        if (exit != 0) throw IOException("RubyInstaller silent install 종료 코드 $exit")
        deployMsys2Toolchain(target, onProgress)
    }

    private fun deployMsys2Toolchain(target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        val msys64Dir = target.resolve("msys64")
        val ucrtBin = msys64Dir.resolve("ucrt64").resolve("bin")
        val gcc = ucrtBin.resolve("gcc.exe")
        val libexecGcc = msys64Dir.resolve("ucrt64").resolve("libexec").resolve("gcc")
        val stdHeader = msys64Dir.resolve("ucrt64").resolve("include").resolve("stdio.h")
        val ld = ucrtBin.resolve("ld.exe")
        val toolchainComplete = Files.exists(gcc) &&
            Files.exists(ld) &&
            Files.isDirectory(libexecGcc) &&
            Files.exists(stdHeader)
        if (toolchainComplete) {
            onProgress(LspInstaller.Progress.CommandOutput("[info] msys64/ucrt64 toolchain 검증 통과 (gcc+ld+libexec+stdio.h), 번들 다운로드 건너뜀"))
            return
        }
        if (Files.exists(msys64Dir)) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] msys64 부분 설치 잔해 정리 중 (gcc.exe=${Files.exists(gcc)}, ld.exe=${Files.exists(ld)}, libexec/gcc=${Files.isDirectory(libexecGcc)}, stdio.h=${Files.exists(stdHeader)})…",
            ))
            ArchiveExtractors.deleteRecursively(msys64Dir)
        }
        val bundleUrl = msys2BundleUrl()
        onProgress(LspInstaller.Progress.Extracting("Downloading PAGE prebuilt MSYS2 + MinGW UCRT64 bundle…"))
        onProgress(LspInstaller.Progress.CommandOutput("> GET $bundleUrl"))
        val tmp = Files.createTempFile("page-msys2-bundle-", ".zip")
        try {
            downloader(bundleUrl, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            onProgress(LspInstaller.Progress.Extracting("Extracting MSYS2 + MinGW UCRT64 bundle to $msys64Dir …"))
            zipExtractor(tmp, msys64Dir, 1)
        } catch (t: Throwable) {
            throw IOException(
                "PAGE prebuilt MSYS2 번들 다운로드/추출 실패 ($bundleUrl): ${t.javaClass.simpleName}: ${t.message}\n" +
                    "복구 절차: 네트워크 확인 후 PAGE 에서 install 재시도. 문제가 지속되면 GitHub releases 에서 ${msys2BundleRelease} asset 가 게시됐는지 확인하세요.",
                t,
            )
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
        if (!Files.exists(gcc)) {
            throw IOException(
                "MSYS2 번들 추출 후에도 gcc.exe 누락: $gcc — zip 구조가 예상과 다릅니다 (msys64/ucrt64/bin/gcc.exe 경로 필요).",
            )
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] MSYS2 + MinGW UCRT64 toolchain 배포 완료: $gcc"))
    }

    internal fun msys2BundleUrl(): String =
        "https://github.com/$msys2BundleRepo/releases/download/$msys2BundleRelease/msys2-mingw-ucrt64-x86_64.zip"

    internal fun downloadUrl(version: String): String = when (osKey) {
        "windows" -> {
            val arch = if (archKey == "arm64") "arm" else "x64"
            "https://github.com/oneclick/rubyinstaller2/releases/download/RubyInstaller-$version-$rubyInstallerRelease/rubyinstaller-devkit-$version-$rubyInstallerRelease-$arch.exe"
        }
        "macos" -> {
            val slug = if (archKey == "arm64") "arm64_big_sur" else macBottleSlug
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/$version/portable-ruby-$version.$slug.bottle.tar.gz"
        }
        else -> throw IOException("RubyBootstrapInstaller 는 Linux 를 지원하지 않습니다. (osKey=$osKey)")
    }

    fun gemBinary(version: String): Path =
        rubyRoot(version).resolve("bin").resolve(if (isWindows) "gem.cmd" else "gem")

    private fun gemInvocation(gemBin: Path, args: List<String>): List<String> =
        if (isWindows) listOf("cmd.exe", "/c", gemBin.toString()) + args
        else listOf(gemBin.toString()) + args

    fun rubyBinary(version: String): Path =
        rubyRoot(version).resolve("bin").resolve(if (isWindows) "ruby.exe" else "ruby")

    fun solargraphBinary(version: String): Path =
        gemHomeFor(version).resolve("bin").resolve(if (isWindows) "solargraph.bat" else "solargraph")

    fun findInstalledSolargraph(version: String): Path? {
        val binDir = gemHomeFor(version).resolve("bin")
        return solargraphCandidateNames()
            .map { binDir.resolve(it) }
            .firstOrNull { Files.exists(it) }
    }

    private fun solargraphCandidateNames(): List<String> =
        if (isWindows) listOf("solargraph.bat", "solargraph.cmd", "solargraph") else listOf("solargraph")

    fun rubyRoot(version: String): Path = rubyBase().resolve(version)

    fun gemHomeFor(version: String): Path = rubyRoot(version).resolve("gemhome")

    private fun rubyBase(): Path = LspInstaller.lspHome().resolve("ruby-bootstrap")

    override fun installDir(version: String?): Path {
        val v = version ?: defaultRubyVersion
        return rubyRoot(v)
    }

    private fun pathEnv(version: String): String {
        val sep = if (isWindows) ";" else ":"
        val rubyBin = rubyRoot(version).resolve("bin").toString()
        val gemBin = gemHomeFor(version).resolve("bin").toString()
        val current = System.getenv("PATH") ?: ""
        if (!isWindows) return rubyBin + sep + gemBin + sep + current
        val msys2Ucrt = rubyRoot(version).resolve("msys64").resolve("ucrt64").resolve("bin").toString()
        val msys2Usr = rubyRoot(version).resolve("msys64").resolve("usr").resolve("bin").toString()
        return listOf(msys2Ucrt, msys2Usr, rubyBin, gemBin, current).joinToString(sep)
    }

    private fun buildInstallEnv(version: String, gemHome: Path): Map<String, String> {
        val base = mutableMapOf(
            "GEM_HOME" to gemHome.toString(),
            "GEM_PATH" to gemHome.toString(),
            "PATH" to pathEnv(version),
        )
        if (!isWindows) return base
        val msys2Root = rubyRoot(version).resolve("msys64").toString()
        base["MSYSTEM"] = "UCRT64"
        base["MSYSTEM_PREFIX"] = "/ucrt64"
        base["MSYSTEM_CARCH"] = "x86_64"
        base["MSYSTEM_CHOST"] = "x86_64-w64-mingw32"
        base["MINGW_CHOST"] = "x86_64-w64-mingw32"
        base["MINGW_PREFIX"] = "/ucrt64"
        base["MINGW_PACKAGE_PREFIX"] = "mingw-w64-ucrt-x86_64"
        base["RI_DEVKIT"] = msys2Root
        base["ACLOCAL_PATH"] = "/ucrt64/share/aclocal:/usr/share/aclocal"
        base["MANPATH"] = "/ucrt64/share/man"
        base["PKG_CONFIG_PATH"] = "/ucrt64/lib/pkgconfig:/ucrt64/share/pkgconfig"
        return base
    }

    fun currentInstalledVersion(): String? {
        val pointer = rubyBase().resolve("CURRENT")
        return runCatching { Files.readString(pointer).trim().takeIf { it.isNotEmpty() } }.getOrNull()
    }

    private fun writePointer(version: String) {
        val pointer = rubyBase().resolve("CURRENT")
        Files.createDirectories(pointer.parent)
        Files.writeString(pointer, version)
    }

    companion object {
        const val DEFAULT_RUBY_VERSION = "3.4.6"
        const val DEFAULT_RUBYINSTALLER_RELEASE = "1"
        const val DEFAULT_MAC_BOTTLE_SLUG = "el_capitan"
        const val DEFAULT_SOLARGRAPH_VERSION = "0.55.4"
        const val PRISM_FREE_RBS_VERSION = "3.3.0"
        const val DEFAULT_MSYS2_BUNDLE_RELEASE = "msys2-bundle-ucrt64-v1"
        const val DEFAULT_MSYS2_BUNDLE_REPO = "monkshark/page-ide"
    }
}
