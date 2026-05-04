# QuickOpenDialog

> 한국어: [quick_open_dialog.md](https://monkshark.github.io/PAGE_IDE/#modules/app/quick_open_dialog.md)

> `page/app/src/main/kotlin/page/app/QuickOpenDialog.kt` — `Ctrl+P` Quick Open dialog

VS Code-style "go to file". Open any file by name without expanding the sidebar.

---

## Signature

```kotlin
@Composable
internal fun QuickOpenDialog(
    files: List<IndexedFile>,
    onPick: (IndexedFile) -> Unit,
    onDismiss: () -> Unit,
)
```

`files` is the index `Main` builds with `ProjectFileIndex.walk(rootDir)` the moment `Ctrl+P` fires. The dialog never touches disk — it only matches and renders.

---

## Keymap

| Key | Effect |
|---|---|
| Character input | Updates `query`, recomputes `QuickOpen.rank(query, files)`, resets `selected` to 0 |
| `↓` / `↑` | Move selection, list auto-scrolls |
| `Enter` | Calls `onPick` with the file at `selected`; closes |
| `Esc` | Calls `onDismiss`; closes |
| Row click | Calls `onPick`; closes |

The `onPreviewKeyEvent` only consumes `KeyDown` events, avoiding `Enter` firing twice.

---

## Layout

```
┌─ Surface (640×420, undecorated) ──────────────┐
│ ┌─ QueryInput (32dp, BasicTextField) ──────┐ │
│ └──────────────────────────────────────────┘ │
│   spacer 8dp                                  │
│ ┌─ ResultList (LazyColumn) ────────────────┐ │
│ │  Foo.kt           src/                    │ │ ← selected row uses primary @ 18% bg
│ │  Foo_en.md        docs/                   │ │
│ │  ...                                      │ │
│ └──────────────────────────────────────────┘ │
└───────────────────────────────────────────────┘
```

`undecorated = true` + `Surface(border)` — same pattern as `UnsavedChangesDialog`, so the glass theme tone matches.

---

## Match highlight

```kotlin
private fun highlightedName(
    text: String,
    indices: IntArray,
    base: Color,
    highlight: Color,
): AnnotatedString
```

Only glyphs at `QuickOpenResult.nameIndices` are painted in `primary` + `Bold`. The parent path (`parent = relative.dropLast(name.length).trimEnd('/')`) is rendered in dim `onSurfaceVariant` with no highlight — name-match alone is plenty for now.

---

## Empty result

If `rank` returns an empty list the dialog shows "결과 없음" centered. `Enter` is a no-op (`getOrNull(0)` is null).

---

## Used by

| Location | Trigger |
|---|---|
| `page.app.Main` `openQuickOpen` | `Ctrl+P` (only when `rootDir != null`) |

If no folder is open (`rootDir == null`), the dialog doesn't appear — there's nothing to index.

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
