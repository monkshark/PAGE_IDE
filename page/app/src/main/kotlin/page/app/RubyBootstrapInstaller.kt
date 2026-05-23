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
    private val bundleOverridePath: () -> String? = { System.getenv("PAGE_RUBY_BUNDLE_OVERRIDE") },
    private val defaultRubyVersion: String = DEFAULT_RUBY_VERSION,
    private val macBottleSlug: String = DEFAULT_MAC_BOTTLE_SLUG,
    private val solargraphPackage: String = "solargraph",
    private val solargraphVersion: String = DEFAULT_SOLARGRAPH_VERSION,
    private val rubyBundleRelease: String = DEFAULT_RUBY_BUNDLE_RELEASE,
    private val rubyBundleRepo: String = DEFAULT_RUBY_BUNDLE_REPO,
) : LspInstaller {

    override val languageId: String = "ruby"
    override val displayName: String = "solargraph"
    override val precheck: LspInstaller.Precheck = LspInstaller.Precheck.Ok
    override val heavyInstall: LspInstaller.HeavyInstallEstimate? = LspInstaller.HeavyInstallEstimate(
        sizeEstimate = if (isWindows) "약 700 MB ~ 1 GB (Ruby + MinGW UCRT64 + solargraph 통합 번들)" else "약 25 MB",
        durationEstimate = "약 3분 ~ 7분",
        notes = if (isWindows)
            "PAGE 가 미리 빌드해 둔 Ruby + MSYS2 + solargraph all-in-one zip 번들을 받아 격리 디렉터리에 풀기만 합니다. " +
                "사용자 환경에서 gem install 이나 MSYS2 toolchain 부트스트랩을 실행하지 않으므로 ASR/Defender 가 fork 차단해도 영향이 없습니다."
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
            if (isWindows) installFromPrebuiltBundle(resolved, onProgress)
            else installFromMacBottle(resolved, onProgress)

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

    private fun installFromPrebuiltBundle(version: String, onProgress: (LspInstaller.Progress) -> Unit) {
        val target = rubyRoot(version)
        Files.createDirectories(target.parent)

        val solargraph = solargraphBinary(version)
        val ruby = rubyBinary(version)
        if (Files.exists(solargraph) && Files.exists(ruby)) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] 기존 설치 발견 (solargraph.bat + ruby.exe) — 번들 다운로드 건너뜀: $target",
            ))
            return
        }
        if (Files.exists(target)) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] $target 에 부분 설치 잔해 (solargraph.bat=${Files.exists(solargraph)}, ruby.exe=${Files.exists(ruby)}) — 삭제 후 재추출",
            ))
            ArchiveExtractors.deleteRecursively(target)
        }
        Files.createDirectories(target)

        detectThirdPartyAntivirus(target, onProgress)
        requestDefenderExclusion(target, onProgress)

        val bundle = obtainBundleZip(version, onProgress)
        onProgress(LspInstaller.Progress.Extracting("Extracting Ruby + solargraph bundle to $target …"))
        try {
            zipExtractor(bundle.path, target, 0)
        } finally {
            if (bundle.deleteAfterExtraction) runCatching { Files.deleteIfExists(bundle.path) }
        }

        if (!Files.exists(solargraph)) {
            throw IOException(
                "번들 추출 후 solargraph.bat 누락: $solargraph — zip 구조가 예상과 다릅니다 " +
                    "(zip 루트가 곧 <install_dir>, gemhome/bin/solargraph.bat 경로 필요).",
            )
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] all-in-one bundle 추출 완료: $solargraph"))
    }

    private fun installFromMacBottle(version: String, onProgress: (LspInstaller.Progress) -> Unit) {
        val url = downloadUrl(version)
        val target = rubyRoot(version)
        val tmp = Files.createTempFile("page-ruby-", ".tar.gz")
        try {
            downloader(url, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            onProgress(LspInstaller.Progress.Extracting("Extracting Ruby runtime…"))
            tarGzExtractor(tmp, target, 2)
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }

        val gemBin = gemBinary(version)
        if (!Files.exists(gemBin)) throw IOException("Ruby 부트스트랩 후 gem 누락: $gemBin")
        runCatching { gemBin.toFile().setExecutable(true, false) }
        val rubyBin = rubyBinary(version)
        runCatching { rubyBin.toFile().setExecutable(true, false) }

        val gemHome = gemHomeFor(version)
        Files.createDirectories(gemHome)

        val env = buildInstallEnv(version, gemHome)
        onProgress(LspInstaller.Progress.CommandOutput("[debug] GEM_HOME  = ${env["GEM_HOME"]}"))
        onProgress(LspInstaller.Progress.CommandOutput("[debug] PATH(head)= ${env["PATH"]?.take(300)}…"))

        val rbsCmd = gemInvocation(gemBin, listOf("install", "--no-document", "--version", PRISM_FREE_RBS_VERSION, "rbs"))
        onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --version $PRISM_FREE_RBS_VERSION rbs (prism-free pin)"))
        val rbsExit = processRunner.runStreaming(rbsCmd, env) { line ->
            onProgress(LspInstaller.Progress.CommandOutput(line))
        }
        if (rbsExit != 0) throw IOException("gem install rbs 종료 코드 $rbsExit")

        val installCmd = gemInvocation(
            gemBin,
            listOf(
                "install", "--no-document", "--conservative",
                "--version", solargraphVersion,
                solargraphPackage,
            ),
        )
        onProgress(LspInstaller.Progress.CommandOutput("> gem install --no-document --conservative --version $solargraphVersion $solargraphPackage"))
        val exit = processRunner.runStreaming(installCmd, env) { line ->
            onProgress(LspInstaller.Progress.CommandOutput(line))
        }
        if (exit != 0) throw IOException("gem install solargraph 종료 코드 $exit")
    }

    private data class BundleZip(val path: Path, val deleteAfterExtraction: Boolean)

    private fun obtainBundleZip(version: String, onProgress: (LspInstaller.Progress) -> Unit): BundleZip {
        val overrideRaw = bundleOverridePath()?.trim().orEmpty()
        if (overrideRaw.isNotEmpty()) {
            val overridePath = runCatching { Path.of(overrideRaw) }.getOrNull()
            when {
                overridePath == null -> onProgress(LspInstaller.Progress.CommandOutput(
                    "[warning] PAGE_RUBY_BUNDLE_OVERRIDE 가 유효한 경로가 아닙니다 ('$overrideRaw') — 무시하고 일반 다운로드 사용",
                ))
                !Files.isRegularFile(overridePath) -> onProgress(LspInstaller.Progress.CommandOutput(
                    "[warning] PAGE_RUBY_BUNDLE_OVERRIDE 가 가리키는 zip 이 존재하지 않습니다 ('$overridePath') — 무시하고 일반 다운로드 사용",
                ))
                else -> {
                    onProgress(LspInstaller.Progress.CommandOutput(
                        "[info] PAGE_RUBY_BUNDLE_OVERRIDE → $overridePath 사용 (다운로드 건너뜀)",
                    ))
                    return BundleZip(overridePath, deleteAfterExtraction = false)
                }
            }
        }

        val url = rubyBundleUrl(version)
        onProgress(LspInstaller.Progress.Extracting("Downloading PAGE Ruby + solargraph bundle…"))
        onProgress(LspInstaller.Progress.CommandOutput("> GET $url"))
        val tmp = Files.createTempFile("page-ruby-bundle-", ".zip")
        try {
            downloader(url, tmp) { read, total ->
                onProgress(LspInstaller.Progress.Downloading(read, total))
            }
            return BundleZip(tmp, deleteAfterExtraction = true)
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(tmp) }
            throw IOException(buildBundleDownloadDiagnostic(url, t), t)
        }
    }

    private fun buildBundleDownloadDiagnostic(url: String, cause: Throwable): String =
        "PAGE Ruby+solargraph 번들 다운로드 실패 ($url): ${cause.javaClass.simpleName}: ${cause.message}\n" +
            "복구 절차:\n" +
            "  1. 네트워크 / 사내 proxy 확인 후 PAGE 에서 install 재시도\n" +
            "  2. 다른 PC 에서 위 URL 의 zip 을 받아 옮긴 뒤,\n" +
            "     환경변수 PAGE_RUBY_BUNDLE_OVERRIDE 에 해당 zip 의 절대 경로를 설정하고 install 재시도\n" +
            "  3. GitHub releases 에 '$rubyBundleRelease' asset 가 publish 됐는지 확인"

    private fun detectThirdPartyAntivirus(target: Path, onProgress: (LspInstaller.Progress) -> Unit) {
        val script = "Get-CimInstance -Namespace root/SecurityCenter2 -ClassName AntiVirusProduct " +
            "-ErrorAction SilentlyContinue | Select-Object -ExpandProperty displayName"
        val cmd = listOf("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script)
        val output = try {
            processRunner.captureOutput(cmd)
        } catch (t: Throwable) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] AV 감지 PowerShell 호출 실패 (${t.javaClass.simpleName}) — 건너뜀",
            ))
            return
        }
        val products = output.lines().map(String::trim).filter(String::isNotEmpty)
        val nonDefender = products.filter { !it.equals("Windows Defender", ignoreCase = true) }
        if (nonDefender.isEmpty()) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[info] AV 감지: Defender 외 활성 AV 없음 — Defender 예외 등록만 진행",
            ))
            return
        }
        onProgress(LspInstaller.Progress.CommandOutput(
            "[warning] Non-Defender AV 감지됨: ${nonDefender.joinToString(", ")}.\n" +
                "  PAGE 의 Defender ExclusionPath 등록은 이 AV 에 적용되지 않습니다.\n" +
                "  install 중 zip 추출 파일이 검역될 수 있습니다.\n" +
                "  권장 — 해당 AV 에서 다음 경로를 예외 등록 후 install 재시도: $target",
        ))
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
                "[warning] UAC PowerShell 실행 실패 (${t.javaClass.simpleName}: ${t.message}).\n" +
                    "  install 은 계속 진행하지만 Defender RTP 가 zip 추출 중 파일을 검역할 수 있습니다.\n" +
                    "  수동 우회 — 관리자 PowerShell 에서 다음 명령 실행 후 install 재시도:\n" +
                    "    Add-MpPreference -ExclusionPath '$target'",
            ))
            return false
        }
        if (exit != 0) {
            onProgress(LspInstaller.Progress.CommandOutput(
                "[warning] Defender exclusion 요청 실패 (exit=$exit — UAC 거부 또는 GPO/엔터프라이즈 Defender 정책으로 차단).\n" +
                    "  install 은 계속 진행하지만 Defender RTP 가 zip 추출 중 파일을 검역할 수 있습니다.\n" +
                    "  수동 우회 — 관리자 PowerShell 에서 다음 명령 실행 후 install 재시도:\n" +
                    "    Add-MpPreference -ExclusionPath '$target'",
            ))
            return false
        }
        onProgress(LspInstaller.Progress.CommandOutput("[info] Defender exclusion 등록 시도 완료 (exit=0)"))
        return true
    }

    internal fun rubyBundleUrl(version: String): String =
        "https://github.com/$rubyBundleRepo/releases/download/$rubyBundleRelease/page-ruby-solargraph-windows-x86_64-$version.zip"

    internal fun downloadUrl(version: String): String = when (osKey) {
        "macos" -> {
            val slug = if (archKey == "arm64") "arm64_big_sur" else macBottleSlug
            "https://github.com/Homebrew/homebrew-portable-ruby/releases/download/$version/portable-ruby-$version.$slug.bottle.tar.gz"
        }
        "windows" -> rubyBundleUrl(version)
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
        const val DEFAULT_MAC_BOTTLE_SLUG = "el_capitan"
        const val DEFAULT_SOLARGRAPH_VERSION = "0.55.4"
        const val PRISM_FREE_RBS_VERSION = "3.3.0"
        const val DEFAULT_RUBY_BUNDLE_RELEASE = "ruby-bundle-win-v1"
        const val DEFAULT_RUBY_BUNDLE_REPO = "monkshark/page-ide"
    }
}
