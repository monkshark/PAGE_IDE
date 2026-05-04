# editor

> `page/editor/` — text buffer, edit operations, syntax highlighting, tab model

Pure-logic module with no UI framework dependency. Compose widgets and key events are handled by the caller (`page:app`); this module only owns *what* gets edited and *how*. Every operation is a pure `TextEdit → TextEdit?` function and is unit-tested as-is

> 한국어: [editor.md](https://monkshark.github.io/PAGE_IDE/#modules/editor.md)

---

## Dependencies

| Kind | Content |
|---|---|
| External | none (Kotlin stdlib only) |
| Internal | `page:core` (shared types like `Identity`) |

No UI / Compose / IO → all edit operations testable as pure functions

---

## `TextEdit`

```kotlin
data class TextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)
```

I/O type for most edit functions. Bare caret when `selectionStart == selectionEnd`, otherwise a selection range. Reverse selections (anchor / active swapped) are also representable as-is

---

## `EditSnapshot`

```kotlin
data class EditSnapshot(val text: String, val caret: Int)
```

One element of `EditHistory`'s past / future stacks — a single undo step

---

## `TextBuffer`

```kotlin
class TextBuffer(val text: String) {
    val length: Int
    val lineCount: Int
    fun lineColOf(offset: Int): Pair<Int, Int>
    fun offsetOf(line: Int, col: Int): Int
}
```

Text wrapper with a cached line index. Powers status bar Ln/Col and line-number gutter coordinate conversions. Text itself is immutable; only the line index is computed lazily

---

## Tab model

### `OpenTab`

```kotlin
data class OpenTab(
    val path: Path,
    val text: String,
    val savedText: String = text,
    val caret: Int = 0,
    val history: EditHistory = EditHistory(),
)
```

Edit state for one file. `dirty` is derived as `text != savedText` → drives the dirty dot and the close-confirmation dialog

### `TabBook`

`List<OpenTab>` plus an active index. Immutable — every mutation returns a new instance

| Method | Behavior |
|---|---|
| `openOrFocus(path, text)` | Focus the existing tab if `path` is already open, otherwise append a new tab |
| `close(index)` / `closeActive()` | Adjusts active index correctly |
| `move(from, to)` | Drag-reorder, tracks active tab |
| `pushHistoryOnActive(prev)` | Delegates to active tab's `EditHistory.pushBeforeChange` |
| `undoOnActive(curr)` / `redoOnActive(curr)` | Undo / redo on active tab's history |
| `markActiveSaved()` | Clears dirty after a save (`savedText = text`) |
| `updateActive(text, caret)` | Apply edit result |

The active-index correction logic (close, move, etc.) is verified by 21 unit tests

---

## Edit operations

All defined as `object` with pure functions. `TextEdit` → `TextEdit` or `null` (no-op)

### `Indent`

| Key | Behavior | Non-Markdown | Inside Markdown fence |
|---|---|---|---|
| Tab (empty selection) | Up to next tab stop | 4 spaces | `\t` |
| Tab (single-line non-empty selection) | Indent at line start, preserve selection | 4 spaces | `\t` |
| Tab (multi-line selection) | Indent each line | 4 spaces | `\t` |
| Shift+Tab | Outdent one unit | 4 spaces / 1 tab | same |
| Backspace | Remove one unit when caret is in indent | — | — |
| Enter | Auto-indent after `{` `(` `[` `:`; pair-split (`{}` etc.) opens a new line | — | — |
| `}` `]` `)` typed | Outdent one unit if line is whitespace-only before | — | — |

The fence-vs-prose branch is decided by the caller (`page:app`'s `EditorPanel`) using [`MarkdownFence.isInsideFence`](#markdownfence)

### `AutoClose`

```kotlin
fun handle(edit: TextEdit, ch: Char): TextEdit?
```

Auto-inserts the matching closer on `(`, `[`, `{`, `"`, `'`. Wraps the selection if a selection exists. Typing the closer over an auto-inserted closer collapses to a single forward step

### `BracketMatch`

```kotlin
fun find(text: String, caret: Int): Pair<Int, Int>?
```

Locates the matching bracket next to the caret. Returns an `(open, close)` index pair. Positions inside strings or comments are ignored. The caller draws the highlight at both indices

### `MarkdownFence`

```kotlin
fun isInsideFence(text: String, caret: Int): Boolean
```

Tells whether the caret is inside a fenced code block (```` ``` ```` / `~~~`). Line-by-line walk — leading spaces ≤ 3, three or more backticks/tildes opens or closes a fence. **The fence-delimiter line itself counts as *outside*** (Tab on that line uses the prose path)

### `LineMove`

`Alt+Up`, `Alt+Down`, `Alt+Shift+Up`, `Alt+Shift+Down` — line move / duplicate. All lines covered by a selection are moved as one block

---

## Search & replace

### `SearchState`

```kotlin
data class SearchState(
    val query: String = "",
    val replace: String = "",
    val caseSensitive: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val activeMatchIndex: Int = -1,
) {
    fun next(): SearchState
    fun prev(): SearchState
    fun recompute(text: String): SearchState
}
```

`recompute(text)` rebuilds the full match list when query changes. `next()` / `prev()` cycle the active match

### `Replace`

```kotlin
fun replace(text: String, state: SearchState): Pair<String, SearchState>?
fun replaceAll(text: String, state: SearchState): Pair<String, SearchState>
```

`replace` swaps only the active match; `replaceAll` does a bulk swap and re-pins the active index

---

## Undo / redo

### `EditHistory`

```kotlin
data class EditHistory(
    val past: List<EditSnapshot> = emptyList(),
    val future: List<EditSnapshot> = emptyList(),
) {
    fun pushBeforeChange(prev: EditSnapshot, maxSize: Int = MAX_SIZE): EditHistory
    fun undo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
    fun redo(current: EditSnapshot): Pair<EditHistory, EditSnapshot>?
    companion object { const val MAX_SIZE = 1000 }
}
```

Immutable past / future stacks — a new edit clears `future`, consecutive identical pushes collapse, capped at 1000 steps

**Per-file** — lives on `OpenTab`, so swapping the active tab swaps the history automatically. `BasicTextField`'s built-in undo is bypassed by intercepting Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y at `Window.onPreviewKeyEvent` → the application layer becomes the single source of truth

> 📎 Design notes: [devlog #4](https://monkshark.github.io/p/page-ide-undo-per-file/)

---

## Syntax highlighting

### `SyntaxLexer`

```kotlin
interface SyntaxLexer {
    fun tokenize(text: String): List<Token>
}

data class Token(val range: IntRange, val kind: TokenKind)

enum class TokenKind { KEYWORD, STRING, NUMBER, COMMENT, ANNOTATION, TYPE, PUNCT }
```

Common interface for language-specific lexers. `SyntaxLexers.forPath(path)` maps by extension

| Extension | lexer |
|---|---|
| `.kt`, `.kts` | `KotlinLexer` (delegates to `JvmLexer`) |
| `.java` | `JavaLexer` (delegates to `JvmLexer`) |
| `.json` | `JsonLexer` |

Long-term we plan to switch to Tree-sitter; for the first stage a regex / state-machine combo is fast and simple enough

---

## File model

> Overlaps the workspace area, but for now lives in this module. Will move once `page:workspace` is split out

| Type | Purpose |
|---|---|
| `FileKind` | text / image / SVG / PDF / other |
| `FileDocument` | path, kind, load result |
| `FileTree` | directory node toggle / expansion state |

---

## Testing principle

Every edit operation is verified *without Compose*. One input (`TextEdit`) → one output (`TextEdit?`) keeps assertions tight and clear

| Area | Test count |
|---|---|
| `Indent` | 53 |
| `MarkdownFence` | 14 |
| `EditHistory` | 8 |
| `TabBook` (active-index correction) | 21 |

```bash
./gradlew :page:editor:test
```

---

- [Back to index](https://monkshark.github.io/PAGE_IDE/#README_en.md)
- [Architecture](https://monkshark.github.io/PAGE_IDE/#guides/architecture_en.md)
