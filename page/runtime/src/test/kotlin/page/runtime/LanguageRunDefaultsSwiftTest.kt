package page.runtime

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LanguageRunDefaultsSwiftTest {

    @Test
    fun prelaunchCarriesLinkerAndBuiltinHeaderFlags() {
        val cmd = LanguageRunDefaults.swiftWindowsPrelaunch("swiftc.exe", "main.swift", "out.exe")
        assertEquals("swiftc.exe", cmd.first())
        assertTrue("main.swift" in cmd)
        assertTrue("-use-ld=lld" in cmd, cmd.toString())
        val builtin = listOf("-Xcc", "-Xclang", "-Xcc", "-fbuiltin-headers-in-system-modules")
        val start = cmd.indexOfFirst { it == "-Xcc" }
        assertEquals(builtin, cmd.subList(start, start + builtin.size), cmd.toString())
        assertEquals(listOf("-o", "out.exe"), cmd.subList(cmd.size - 2, cmd.size))
    }

    @Test
    fun prelaunchPassesLinkLibsAsPositionalLinkerInputsBeforeOutput() {
        val lib = "C:\\swift\\sdk\\usr\\lib\\swift\\windows\\x86_64\\Foundation.lib"
        val cmd = LanguageRunDefaults.swiftWindowsPrelaunch("swiftc.exe", "main.swift", "out.exe", listOf(lib))
        val xlink = cmd.indexOf("-Xlinker")
        assertTrue(xlink >= 0, cmd.toString())
        assertEquals(lib, cmd[xlink + 1], cmd.toString())
        assertTrue(xlink < cmd.indexOf("-o"), "linker inputs must precede -o: $cmd")
        assertEquals(listOf("-o", "out.exe"), cmd.subList(cmd.size - 2, cmd.size))
    }

    @Test
    fun windowsConfigCompilesToExeThenRunsit() {
        val root: Path = Path("C:", "ws")
        val file = root.resolve("hello.swift")
        val swiftc = Path("C:", "swift", "bin", "swiftc.exe")
        val cfg = LanguageRunDefaults.buildSwiftWindowsConfig(
            path = file,
            fileName = "hello.swift",
            baseName = "hello",
            workspaceRoot = root,
            swiftc = swiftc,
        )
        assertNotNull(cfg)
        val exe = root.resolve("hello.exe").toAbsolutePath().toString()
        assertEquals(exe, cfg.command)
        assertTrue(cfg.args.isEmpty())
        assertEquals(root.toString(), cfg.workingDir)
        val pre = assertNotNull(cfg.prelaunch)
        assertEquals(swiftc.toAbsolutePath().toString(), pre.first())
        assertEquals(listOf("-o", exe), pre.subList(pre.size - 2, pre.size))
        assertTrue("-use-ld=lld" in pre)
    }
}
