# ProjectFileIndex

> 한국어: [project_file_index.md](https://monkshark.github.io/page-ide/#modules/editor/project_file_index.md)

> `page/editor/src/main/kotlin/page/editor/ProjectFileIndex.kt` — Flattening walker over the project root

When `Ctrl+P` opens, this scans every file under `rootDir` and returns a flat list. Heavy directories are skipped, and the walk stops at a 5000-entry limit.

---

## `walk`

```kotlin
fun walk(root: Path, limit: Int = 5000): List<IndexedFile>
```

DFS via an `ArrayDeque` stack. Directories pass the SKIP check and a hidden-name filter (`.`-prefixed); files become `IndexedFile(path, root.relativize(child))`. The final list is sorted by lowercase `relative`.

| Skipped |
|---|
| `.git`, `.idea`, `.gradle`, `.kotlin`, `.vscode` |
| `build`, `out`, `node_modules`, `target`, `dist`, `.cache`, `bin` |

`limit` defaults to 5000. Anything past that is dropped, which is fine — Quick Open is a human-eye picker, not a full-text search.

---

## `IndexedFile`

```kotlin
data class IndexedFile(val path: Path, val relative: String)
```

`path` stays absolute (handed to `openInTab` as-is). `relative` is always `/`-normalized (Windows `\` is rewritten). Only `relative` is fed to the matcher.

---

## Used by

| Location | Role |
|---|---|
| `page.app.Main.openQuickOpen` | Called once when `Ctrl+P` fires; result cached into `quickOpenIndex` state |

The index isn't kept around while the dialog is closed — the next open re-walks. So adds/deletes don't go stale.

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
