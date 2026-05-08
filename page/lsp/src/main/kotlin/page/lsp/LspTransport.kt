package page.lsp

import java.io.InputStream
import java.io.OutputStream

interface LspTransport : AutoCloseable {
    val input: InputStream
    val output: OutputStream
}

class StreamTransport(
    override val input: InputStream,
    override val output: OutputStream,
    private val onClose: () -> Unit = {},
) : LspTransport {
    override fun close() {
        try { input.close() } catch (_: Throwable) {}
        try { output.close() } catch (_: Throwable) {}
        onClose()
    }
}

class ProcessTransport(
    private val process: Process,
    private val onStderrLine: (String) -> Unit = { System.err.println("[lsp] $it") },
) : LspTransport {
    override val input: InputStream = process.inputStream
    override val output: OutputStream = process.outputStream
    val errorStream: InputStream = process.errorStream

    private val stderrPump: Thread = Thread({
        try {
            errorStream.bufferedReader().useLines { lines ->
                for (line in lines) onStderrLine(line)
            }
        } catch (_: Throwable) {
        }
    }, "lsp-stderr-pump").apply {
        isDaemon = true
        start()
    }

    override fun close() {
        try { input.close() } catch (_: Throwable) {}
        try { output.close() } catch (_: Throwable) {}
        try { errorStream.close() } catch (_: Throwable) {}
        try { stderrPump.interrupt() } catch (_: Throwable) {}
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }
}
