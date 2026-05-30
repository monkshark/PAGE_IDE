package page.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import page.editor.ProjectGrep
import page.workspace.ReplaceRequest
import java.nio.file.Path

class FileOperationsInteractor(
    private val readFileText: (Path) -> String?,
    private val applyTextReplace: (Path, String) -> Unit,
) {
    data class ReplaceResult(
        val filesChanged: Int,
        val replacements: Int,
        val updates: Map<Path, String>,
    )

    suspend fun replaceInFiles(req: ReplaceRequest): ReplaceResult = withContext(Dispatchers.IO) {
        var filesChanged = 0
        var replacements = 0
        val updates = HashMap<Path, String>()
        for (target in req.targets) {
            val original = readFileText(target) ?: continue
            val (newText, count) = ProjectGrep.applyReplace(
                text = original,
                query = req.query,
                replacement = req.replacement,
                caseSensitive = req.caseSensitive,
                regex = req.regex,
                wholeWord = req.wholeWord,
            )
            if (count == 0 || newText == original) continue
            try {
                applyTextReplace(target, newText)
            } catch (_: java.io.IOException) {
                continue
            }
            updates[target] = newText
            filesChanged += 1
            replacements += count
        }
        ReplaceResult(filesChanged, replacements, updates)
    }
}
