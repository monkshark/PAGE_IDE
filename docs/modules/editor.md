# editor

> `page/editor/` — 텍스트 버퍼·편집 동작·신택스 하이라이팅·탭 모델

UI 프레임워크 의존이 없는 순수 로직 모듈. Compose 위젯과 키 이벤트는 호출자(`page:app`)가 처리하고, 이 모듈은 *무엇을 어떻게 편집하는가* 만 담는다. 모든 동작은 `TextEdit` 입력 → `TextEdit?` 출력으로 단위 테스트만으로 검증 가능

> English: [editor_en.md](https://monkshark.github.io/PAGE_IDE/#modules/editor_en.md)

---

## 의존성

| 종류 | 내용 |
|---|---|
| 외부 | 없음 (Kotlin stdlib only) |
| 내부 | `page:core` (`Identity` 등 공용 타입) |

UI / Compose / IO 의존 없음 → 모든 편집 동작 순수 함수로 검증

---

## `TextEdit`

```kotlin
data class TextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)
```

대부분 편집 함수의 입출력 타입. `selectionStart == selectionEnd` 면 빈 캐럿, 아니면 선택 범위. anchor / active 가 반전된 역방향 선택도 그대로 표현됨

---

## `EditSnapshot`

```kotlin
data class EditSnapshot(val text: String, val caret: Int)
```

`EditHistory` past / future 스택의 원소. undo 한 단계

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

라인 인덱스를 캐시한 텍스트 래퍼. 상태바 Ln/Col 표시와 거터 라인 번호용 좌표 변환 담당. 텍스트 자체는 immutable, 라인 인덱스만 lazy 계산

---

## 탭 모델

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

한 파일의 편집 상태. `dirty` 는 `text != savedText` 로 파생 → dirty 표시점 / 닫기 확인 다이얼로그 트리거에 사용

### `TabBook`

`List<OpenTab>` + 활성 인덱스. immutable, 모든 변경은 새 인스턴스 반환

| 메서드 | 설명 |
|---|---|
| `openOrFocus(path, text)` | 같은 path 가 이미 열려 있으면 포커스 이동, 아니면 새 탭 |
| `close(index)` / `closeActive()` | 활성 인덱스 보정 포함 |
| `move(from, to)` | 드래그 재배치, 활성 탭 추적 |
| `pushHistoryOnActive(prev)` | 활성 탭의 `EditHistory.pushBeforeChange` 위임 |
| `undoOnActive(curr)` / `redoOnActive(curr)` | 활성 탭 히스토리에서 undo / redo |
| `markActiveSaved()` | 저장 후 dirty 해제 (`savedText = text`) |
| `updateActive(text, caret)` | 편집 결과 반영 |

활성 인덱스 보정 로직(close, move 등)은 21개 단위 테스트로 검증

---

## 편집 동작

모두 `object` 로 정의된 순수 함수. `TextEdit` → `TextEdit` 또는 `null` (변경 없음)

### `Indent`

| 키 | 동작 | 비 마크다운 | 마크다운 펜스 안 |
|---|---|---|---|
| Tab (빈 선택) | 다음 탭 스톱까지 | 4 스페이스 | `\t` |
| Tab (단일 줄 비어 있지 않은 선택) | 줄 맨 앞 들여쓰기, 선택 보존 | 4 스페이스 | `\t` |
| Tab (다중 줄 선택) | 각 줄 앞 들여쓰기 | 4 스페이스 | `\t` |
| Shift+Tab | 한 단위 외감기 | 4 스페이스 / 1 탭 | 동일 |
| Backspace | 들여쓰기 안일 때 한 단위 제거 | — | — |
| Enter | `{` `(` `[` `:` 다음 자동 들여쓰기, `{}` 등 짝 분리 시 새 줄 추가 | — | — |
| `}` `]` `)` 입력 | 줄이 들여쓰기뿐이면 한 단위 외감 후 닫기 | — | — |

펜스 안 / 밖 분기는 호출자(`page:app` 의 `EditorPanel`)가 [`MarkdownFence.isInsideFence`](#markdownfence) 결과로 결정

### `AutoClose`

```kotlin
fun handle(edit: TextEdit, ch: Char): TextEdit?
```

`(`, `[`, `{`, `"`, `'` 입력 시 짝 자동 삽입. 선택 범위가 있으면 그 선택을 wrap. 닫는 문자를 그대로 입력한 경우 이미 자동 삽입된 닫는 문자와 겹쳐 한 글자 전진

### `BracketMatch`

```kotlin
fun find(text: String, caret: Int): Pair<Int, Int>?
```

캐럿 옆 괄호와 짝이 되는 위치를 찾아 `(open, close)` 쌍으로 반환. 문자열 / 주석 안 위치는 무시. 호출자가 두 위치에 배경 하이라이트를 그린다

### `MarkdownFence`

```kotlin
fun isInsideFence(text: String, caret: Int): Boolean
```

캐럿이 펜스 (```` ``` ```` / `~~~`) 안인지 판정. 라인 단위 워크 — 들여쓰기 ≤ 3, 백틱·틸드 3개 이상 라인을 펜스 경계로 본다. **펜스 경계 라인 자체는 *바깥*** 으로 친다 (그 줄에서 Tab 은 일반 동작)

### `LineMove`

`Alt+Up`, `Alt+Down`, `Alt+Shift+Up`, `Alt+Shift+Down` — 줄 이동 / 복제. 선택 범위가 걸친 모든 줄을 한 덩어리로 옮긴다

---

## 검색·치환

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

`recompute(text)` 는 query 변경 시 전체 매치 다시 계산. `next()` / `prev()` 는 활성 매치 순환

### `Replace`

```kotlin
fun replace(text: String, state: SearchState): Pair<String, SearchState>?
fun replaceAll(text: String, state: SearchState): Pair<String, SearchState>
```

`replace` 는 활성 매치 한 곳만, `replaceAll` 은 일괄 치환 후 활성 인덱스 보정

---

## undo / redo

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

immutable past / future 스택 — 새 편집은 future 비움, 동일 스냅샷 연속 push 는 collapse, 1000 단계에서 자른다

**파일별 분리** — `OpenTab` 안에 들어 있어 활성 탭이 바뀌면 히스토리도 자동으로 따라 바뀜. `BasicTextField` 의 내장 undo 는 `Window.onPreviewKeyEvent` 에서 Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y 를 가로채 우회 → 응용 계층이 undo 의 단일 진실원

> 📎 설계 배경: [개발기 #4](https://monkshark.github.io/p/page-ide-undo-per-file/)

---

## 신택스 하이라이팅

### `SyntaxLexer`

```kotlin
interface SyntaxLexer {
    fun tokenize(text: String): List<Token>
}

data class Token(val range: IntRange, val kind: TokenKind)

enum class TokenKind { KEYWORD, STRING, NUMBER, COMMENT, ANNOTATION, TYPE, PUNCT }
```

언어별 lexer 의 공통 인터페이스. `SyntaxLexers.forPath(path)` 가 확장자로 매핑

| 확장자 | lexer |
|---|---|
| `.kt`, `.kts` | `KotlinLexer` (`JvmLexer` 위임) |
| `.java` | `JavaLexer` (`JvmLexer` 위임) |
| `.json` | `JsonLexer` |

장기적으로는 Tree-sitter 로 교체 예정. 첫 단계에서는 정규식·상태기계 조합으로 충분

---

## 파일 모델

> 워크스페이스 영역과 일부 겹치지만 현재는 같은 모듈에 둔다. `page:workspace` 분리 시점에 옮긴다

| 타입 | 용도 |
|---|---|
| `FileKind` | 텍스트 / 이미지 / SVG / PDF / 기타 |
| `FileDocument` | path, kind, 로드 결과 |
| `FileTree` | 디렉터리 노드 토글 / 펼침 상태 |

---

## 테스트 원칙

모든 편집 동작은 *Compose 없이* 검증. 한 입력(`TextEdit`) → 한 출력(`TextEdit?`) 이라 단언이 좁고 명확하다

| 영역 | 테스트 수 |
|---|---|
| `Indent` | 53 |
| `MarkdownFence` | 14 |
| `EditHistory` | 8 |
| `TabBook` (활성 인덱스 보정) | 21 |

```bash
./gradlew :page:editor:test
```

---

- [목차로 돌아가기](https://monkshark.github.io/PAGE_IDE/#README.md)
- [아키텍처](https://monkshark.github.io/PAGE_IDE/#guides/architecture.md)
