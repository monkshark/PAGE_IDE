package page.editor

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object FileDocument {

    fun load(path: Path): String =
        Files.readString(path, StandardCharsets.UTF_8)

    fun save(path: Path, text: String) {
        Files.writeString(path, text, StandardCharsets.UTF_8)
    }
}
