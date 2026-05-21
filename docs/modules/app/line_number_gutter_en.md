# LineNumberGutter

> 한국어: [line_number_gutter.md](https://monkshark.github.io/page-ide/#modules/app/line_number_gutter.md)

> `page/app/src/main/kotlin/page/app/LineNumberGutter.kt` — Left-edge line numbers + fold toggles

Hugs the left edge of `EditorPanel`. The current line is bright, the rest are muted, and foldable lines pick up a ▾/▸ toggle.

---

## Signature

```kotlin
@Composable
internal fun LineNumberGutter(
    lines: List<GutterLine>,
    currentOriginalLine: Int,
    onToggleFold: (Int) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
)

internal data class GutterLine(
    val originalLine: Int,
    val foldable: Boolean,
    val folded: Boolean,
)
```

| Parameter | Meaning |
|---|---|
| `lines` | Rows that should actually render — caller pre-filters out fold-hidden lines |
| `currentOriginalLine` | Caret line in original-text coords — the matching row gets the active color |
| `onToggleFold(originalLine)` | Toggle click callback — argument is a 0-indexed original line number |
| `textStyle` | Same font / line height as the body so rows align exactly |

---

## Colors

| Row state | Color |
|---|---|
| `originalLine == currentOriginalLine` | `colorScheme.onBackground` (active) |
| Otherwise | `colorScheme.onSurfaceVariant` (muted) |
| Toggle (folded) | Slightly brighter `colorScheme.primary` — visual signal that the block is collapsed |
| Toggle (unfolded / not foldable) | `onSurfaceVariant` |

---

## Row structure

Each row is `Row { FoldToggle; Text(number) }`. `FoldToggle` is a 14dp clickable `Box` — foldable lines get ▾ (open) / ▸ (folded); everything else gets a space so the column width stays steady.

---

## Alignment

`fillMaxWidth()` + `TextAlign.End` — right-aligned even when the digit count grows. Internally uses `IntrinsicSize.Max`, so the gutter width tracks the largest number (`lineCount`).

The 16dp top/bottom padding has to match `EditorPanel`'s body padding exactly, otherwise gutter rows drift relative to the body.

---

## Filtering rows for folds

The caller (`EditorPanel`) computes `hiddenLines = union of (startLine+1..endLine) for each folded region` and passes only the surviving lines as `lines`. Gutter row count then matches the visible body row count exactly — a folded region collapses to its start line.

---

## Used by

| Location | Purpose |
|---|---|
| `page.app.EditorPanel` | `Row { LineNumberGutter(...); editorBody }` left column |

---

- [Back to index](https://monkshark.github.io/page-ide/#README_en.md)
