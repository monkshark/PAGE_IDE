# QuickOpen

> 한국어: [quick_open.md](https://monkshark.github.io/page-ide/#modules/editor/quick_open.md)

> `page/editor/src/main/kotlin/page/editor/QuickOpen.kt` — Quick Open result ranking

Runs `FuzzyMatcher` over the file list, sums per-file scores, sorts, and trims. The dialog only consumes the resulting `QuickOpenResult` list — it never matches strings itself.

---

## `rank`

```kotlin
fun rank(query: String, files: List<IndexedFile>): List<QuickOpenResult>
```

| Case | Behavior |
|---|---|
| `query` empty or whitespace | First 50 files in input order (score 0, empty indices) |
| Matches neither name nor path | Excluded |
| Matches at least one | Sum score → sort desc → cap at 50 |

Filename matches dominate (`+200` bonus); path matches are scaled down (`/4`). So typing `editor` ranks `Editor.kt` above arbitrary files inside an `editor/` folder.

---

## `QuickOpenResult`

```kotlin
data class QuickOpenResult(
    val file: IndexedFile,
    val nameIndices: IntArray,
    val pathIndices: IntArray,
    val score: Int,
)
```

| Field | Purpose |
|---|---|
| `file` | What to open on selection |
| `nameIndices` | Which glyphs in the filename to highlight |
| `pathIndices` | Path-area highlights (currently unused — parent path is rendered dim) |
| `score` | Sort key |

`equals`/`hashCode` are overridden to do deep comparisons on the `IntArray` fields.

---

## `nameOf` / `nameOffset`

```kotlin
fun nameOf(relative: String): String
fun nameOffset(relative: String): Int
```

`a/b/Foo.kt` → `Foo.kt` / `4`. Splits on the last `/` only — the walker normalizes `\` to `/`, so this stays platform-independent.

---

## Used by

| Location | Role |
|---|---|
| `page.app.QuickOpenDialog` | Recomputes `rank(query, files)` whenever the query changes |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
