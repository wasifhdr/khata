# Notes Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A notepad: rich text with pictures between the lines, written on a ruled page, saved
without being asked to save.

**Architecture:** One `notes` table on migration 13 → 14, whose `content` column holds an ordered
block list as JSON. A pure-Kotlin document engine (`core/note/`) owns every edit — span mapping,
split, merge, kind changes — so the hardest logic is testable without a screen. `feature/notes`
renders one `BasicTextField` per text block on a grid derived from the body line height.

**Tech Stack:** Kotlin · Room · Hilt · Compose (foundation 1.10.6) · kotlinx.serialization · Coil
· Robolectric. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-09-05-khata-notes-design.md`

## Global Constraints

- Every table carries the house quartet: `uuid` (unique index), `createdAt`, `updatedAt`,
  `deletedAt` nullable. Deletes are soft.
- `BackupRepository.SCHEMA_VERSION` must equal the Room `version` at all times (14).
- Migration SQL is copied **verbatim** from `app/schemas/…/14.json` after a build.
- No new dependencies. `kotlinx.serialization`, Coil, Haze, `FieldScaffold`, `KhataGlass`,
  `NavCircle`, `Pill`, `SectionLabel` already exist and are reused.
- **Every vertical dimension in the editor is a whole number of rules**, and `ruleSpacing` is
  derived from the body style's measured line height — never a hardcoded dp.
- The `Json` instance sets `ignoreUnknownKeys = true`; unparseable content opens as one paragraph
  holding the raw text, never as an empty note.
- English interface. Bengali must render *and sit on the rules*.
- Routine controls live in the bottom third; nothing routine in a top corner.

---

### Task 1: The document model and its schema

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/note/Block.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/entity/NoteEntity.kt`
- Create: `app/src/main/java/com/wasif/khata/core/data/dao/NoteDao.kt`
- Modify: `KhataDatabase.kt` (entity, `version = 14`, `noteDao()`), `DatabaseModule.kt`,
  `Migrations.kt`, `BackupRepository.kt`
- Test: `app/src/test/java/com/wasif/khata/core/note/BlockJsonTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/migration/Migration13To14Test.kt`

**Interfaces:**
- Produces: `Block` (`Text`, `Image`), `TextKind`, `Mark`, `Span`, `NoteDocument`,
  `NoteJson.encode(NoteDocument): String`, `NoteJson.decode(String): NoteDocument`;
  `NoteEntity`; `NoteDao` with `upsert`, `findById`, `observeAll(): Flow<List<NoteEntity>>`,
  `observeNote(id): Flow<NoteEntity?>`, `findByIds`, `allIdsForIndex`, `setPinned`, `softDelete`.

- [ ] **Step 1: Write the block model**

```kotlin
package com.wasif.khata.core.note

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class TextKind { PARAGRAPH, HEADING, BULLET, NUMBERED, CHECK }

enum class Mark { BOLD, ITALIC, STRIKE }

@Serializable
data class Span(val start: Int, val end: Int, val mark: Mark)

@Serializable
sealed interface Block {
    val id: String

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: String,
        val kind: TextKind = TextKind.PARAGRAPH,
        val text: String = "",
        val spans: List<Span> = emptyList(),
        /** Meaningful only for CHECK: a paragraph is never "unchecked". */
        val checked: Boolean = false,
    ) : Block

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: String,
        val sha256: String,
        val widthPx: Int,
        val heightPx: Int,
    ) : Block
}

/** A note's whole content. An empty document is one empty paragraph, never zero blocks:
 *  a note with no blocks has nowhere to put the cursor. */
data class NoteDocument(val blocks: List<Block>) {
    companion object {
        fun empty(newId: () -> String) = NoteDocument(listOf(Block.Text(id = newId())))
    }
}

object NoteJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(document: NoteDocument): String = json.encodeToString(document.blocks)

    /**
     * Never throws and never returns nothing. Content this version cannot parse opens as one
     * paragraph holding the raw text, because principle 3 says nothing is ever lost -- including
     * a note the app has stopped understanding.
     */
    fun decode(raw: String, newId: () -> String): NoteDocument = runCatching {
        NoteDocument(json.decodeFromString<List<Block>>(raw)).takeIf { it.blocks.isNotEmpty() }
    }.getOrNull() ?: NoteDocument(listOf(Block.Text(id = newId(), text = raw)))
}
```

- [ ] **Step 2: Write the failing round-trip tests**

```kotlin
class BlockJsonTest {
    private var n = 0
    private fun id() = "b${n++}"

    @Test
    fun `a document round-trips through json`() {
        val doc = NoteDocument(
            listOf(
                Block.Text("b1", TextKind.HEADING, "Shopping"),
                Block.Text("b2", TextKind.CHECK, "Rice", listOf(Span(0, 4, Mark.BOLD)), checked = true),
                Block.Image("b3", "abc123", 800, 600),
            ),
        )
        assertEquals(doc.blocks, NoteJson.decode(NoteJson.encode(doc), ::id).blocks)
    }

    @Test
    fun `an unknown block kind does not make the rest unreadable`() {
        // A future version's block, arriving on a downgrade or a partial restore.
        val raw = """[{"type":"text","id":"b1","text":"kept"},{"type":"sketch","id":"b2"}]"""
        val blocks = NoteJson.decode(raw, ::id).blocks
        assertEquals("kept", (blocks.first() as Block.Text).text)
    }

    @Test
    fun `content that is not json at all opens as one paragraph holding it`() {
        val doc = NoteJson.decode("not json {", ::id)
        assertEquals("not json {", (doc.blocks.single() as Block.Text).text)
    }

    @Test
    fun `an empty document is one empty paragraph, never zero blocks`() {
        assertEquals(1, NoteJson.decode("[]", ::id).blocks.size)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*BlockJsonTest*'` — FAIL, then PASS.

**Note:** an unknown `type` makes `decodeFromString` throw even with `ignoreUnknownKeys`, because
the discriminator has no registered subclass. If the test above fails on the "unknown kind" case,
decode block-by-block through `JsonArray` and drop the elements that fail, rather than losing the
whole note. Keep the test; change the implementation.

- [ ] **Step 3: Write `NoteEntity` and `NoteDao`**

```kotlin
@Entity(
    tableName = "notes",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["updatedAt"])],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /** The block list as JSON. See core/note/Block.kt. */
    val content: String,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
```

`NoteDao`: `@Upsert upsert`, `findById`, `observeNote(id)`, and

```kotlin
@Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY pinned DESC, updatedAt DESC")
fun observeAll(): Flow<List<NoteEntity>>

@Query("SELECT * FROM notes WHERE id IN (:ids) AND deletedAt IS NULL ORDER BY updatedAt DESC")
suspend fun findByIds(ids: List<Long>): List<NoteEntity>

@Query("SELECT id FROM notes WHERE deletedAt IS NULL")
suspend fun allIdsForIndex(): List<Long>

@Query("UPDATE notes SET pinned = :pinned, updatedAt = :now WHERE id = :id")
suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

@Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
suspend fun softDelete(id: Long, now: Long)
```

- [ ] **Step 4: Register, build, copy the schema, write the migration**

Add `NoteEntity::class` and `noteDao()` to `KhataDatabase`, set `version = 14`.

Run: `./gradlew :app:kspDebugKotlin`
Expected: `app/schemas/com.wasif.khata.core.data.KhataDatabase/14.json` written.

Add `MIGRATION_13_14` with statements copied verbatim from `14.json`, register it in
`DatabaseModule`, set `BackupRepository.SCHEMA_VERSION = 14`, and **append `MIGRATION_13_14` to
the `addMigrations(...)` chain in every existing `Migration*To*Test`** — each test migrates to the
current version, so a new migration breaks all of them until it is added.

- [ ] **Step 5: Write `Migration13To14Test`**

Copy `Migration12To13Test`, changing the versions, and assert a note inserts and comes back with
its content intact.

- [ ] **Step 6: Run everything, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*Migration*' --tests '*BlockJsonTest*'`

```bash
git add app/src app/schemas
git commit -m "feat(notes): one table, and a document that survives what it cannot parse"
```

---

### Task 2: The document engine

The hardest logic in the module, with no UI attached to it. Every function here is pure.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/note/NoteEdits.kt`
- Test: `app/src/test/java/com/wasif/khata/core/note/SpanEditTest.kt`
- Test: `app/src/test/java/com/wasif/khata/core/note/BlockEditTest.kt`

**Interfaces:**
- Produces: `applyEdit(spans, replaced: IntRange, insertedLength: Int): List<Span>`,
  `diffRange(old: String, new: String): Pair<IntRange, Int>`,
  `toggleMark(spans, range, mark): List<Span>`,
  `splitBlock(block, offset, newId): Pair<Block.Text, Block.Text>`,
  `mergeBlocks(first, second): Block.Text?`,
  `numberFor(blocks, index): Int?`, `titleOf(document): String?`, `snippetOf(document): String?`,
  `rulesFor(heightPx, ruleSpacingPx): Int`.

- [ ] **Step 1: Write the failing span tests**

```kotlin
class SpanEditTest {
    private fun spans(vararg s: Span) = s.toList()
    private val bold = Mark.BOLD

    @Test
    fun `an insertion before a span shifts it`() {
        val result = applyEdit(spans(Span(5, 10, bold)), replaced = 0..0, insertedLength = 3)
        assertEquals(spans(Span(8, 13, bold)), result)
    }

    @Test
    fun `an insertion inside a span extends it`() {
        val result = applyEdit(spans(Span(0, 5, bold)), replaced = 2..2, insertedLength = 3)
        assertEquals(spans(Span(0, 8, bold)), result)
    }

    @Test
    fun `an insertion after a span leaves it alone`() {
        val result = applyEdit(spans(Span(0, 5, bold)), replaced = 7..7, insertedLength = 3)
        assertEquals(spans(Span(0, 5, bold)), result)
    }

    @Test
    fun `a deletion overlapping a span clips it`() {
        val result = applyEdit(spans(Span(2, 8, bold)), replaced = 5..8, insertedLength = 0)
        assertEquals(spans(Span(2, 5, bold)), result)
    }

    @Test
    fun `a span clipped to nothing is dropped, not kept as empty`() {
        val result = applyEdit(spans(Span(2, 5, bold)), replaced = 2..5, insertedLength = 0)
        assertEquals(emptyList<Span>(), result)
    }

    @Test
    fun `a replacement spanning two spans clips both`() {
        val result = applyEdit(
            spans(Span(0, 4, bold), Span(6, 10, bold)),
            replaced = 3..7,
            insertedLength = 1,
        )
        assertEquals(spans(Span(0, 3, bold), Span(4, 5, bold)), result)
    }

    @Test
    fun `adjacent spans of the same mark merge, so editing cannot grow an unbounded list`() {
        val result = toggleMark(spans(Span(0, 3, bold)), 3..6, bold)
        assertEquals(spans(Span(0, 6, bold)), result)
    }

    @Test
    fun `toggling a mark over a range that already has it removes it`() {
        assertEquals(emptyList<Span>(), toggleMark(spans(Span(0, 6, bold)), 0..6, bold))
    }

    @Test
    fun `diffRange finds the single changed range by trimming both ends`() {
        val (range, inserted) = diffRange("hello world", "hello brave world")
        assertEquals(6..6, range)
        assertEquals(6, inserted)
    }
}
```

- [ ] **Step 2: Run, fail, implement `NoteEdits.kt`, pass**

The mapping rule, stated once in the file:

```kotlin
/**
 * Every edit reduces to one replaced range, so spans map through it by three cases and no
 * more: wholly before is untouched, wholly after shifts by the delta, overlapping is clipped.
 * A span clipped to nothing is dropped rather than kept as an empty range, and adjacent spans
 * of the same mark are merged -- without that, a long editing session grows one span per
 * keystroke and the note's JSON balloons while looking identical on screen.
 */
```

- [ ] **Step 3: Write the failing block tests**

```kotlin
class BlockEditTest {

    @Test
    fun `splitting partitions the spans at the cut`() {
        val block = Block.Text("b1", TextKind.PARAGRAPH, "hello world", listOf(Span(0, 11, Mark.BOLD)))
        val (first, second) = splitBlock(block, offset = 5, newId = "b2")
        assertEquals("hello", first.text)
        assertEquals(" world", second.text)
        assertEquals(listOf(Span(0, 5, Mark.BOLD)), first.spans)
        assertEquals(listOf(Span(0, 6, Mark.BOLD)), second.spans)
    }

    @Test
    fun `splitting a heading leaves a paragraph below it`() {
        val heading = Block.Text("b1", TextKind.HEADING, "Title")
        val (first, second) = splitBlock(heading, offset = 5, newId = "b2")
        assertEquals(TextKind.HEADING, first.kind)
        assertEquals(TextKind.PARAGRAPH, second.kind)
    }

    @Test
    fun `splitting a bullet continues the list`() {
        val bullet = Block.Text("b1", TextKind.BULLET, "milk")
        assertEquals(TextKind.BULLET, splitBlock(bullet, 4, "b2").second.kind)
    }

    @Test
    fun `merging shifts the second block's spans by the first's length`() {
        val a = Block.Text("b1", text = "hello")
        val b = Block.Text("b2", text = " world", spans = listOf(Span(1, 6, Mark.ITALIC)))
        val merged = mergeBlocks(a, b)!!
        assertEquals("hello world", merged.text)
        assertEquals(listOf(Span(6, 11, Mark.ITALIC)), merged.spans)
    }

    @Test
    fun `numbering is derived from contiguous siblings, so inserting renumbers nothing`() {
        val blocks = listOf(
            Block.Text("b1", TextKind.NUMBERED, "one"),
            Block.Text("b2", TextKind.NUMBERED, "two"),
            Block.Text("b3", TextKind.PARAGRAPH, "aside"),
            Block.Text("b4", TextKind.NUMBERED, "restart"),
        )
        assertEquals(1, numberFor(blocks, 0))
        assertEquals(2, numberFor(blocks, 1))
        assertNull(numberFor(blocks, 2))
        assertEquals(1, numberFor(blocks, 3))
    }

    @Test
    fun `the title is the first non-empty text, and an image-first note is untitled`() {
        assertEquals("Shopping", titleOf(NoteDocument(listOf(
            Block.Text("b1", text = "   "),
            Block.Text("b2", text = "Shopping"),
        ))))
        assertNull(titleOf(NoteDocument(listOf(Block.Image("b1", "abc", 10, 10)))))
    }

    @Test
    fun `an image occupies a whole number of rules`() {
        assertEquals(3, rulesFor(heightPx = 200, ruleSpacingPx = 70))   // 2.86 -> 3
        assertEquals(2, rulesFor(heightPx = 140, ruleSpacingPx = 70))   // exact
    }
}
```

- [ ] **Step 4: Run, fail, implement, pass, commit**

```bash
git add app/src
git commit -m "feat(notes): the edits, as functions with no screen attached"
```

---

### Task 3: Repository, autosave, and search

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/data/repository/NoteRepository.kt`
- Create: `app/src/main/java/com/wasif/khata/core/search/NoteIndexSource.kt`
- Modify: `RepositoryModule.kt` (`@IntoSet`), `DatabaseModule.kt` (`provideNoteDao`),
  `TestSearchIndex.kt`
- Test: `app/src/test/java/com/wasif/khata/core/data/repository/NoteRepositoryTest.kt`

**Interfaces:**
- Produces: `ENTITY_NOTE = "note"`, `NoteRepository.create(): Long`, `save(id, NoteDocument)`,
  `observeAll()`, `observeNote(id)`, `setPinned(id, Boolean)`, `delete(id)`,
  `plainTextOf(NoteDocument): String`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `saving replaces the document and moves the note to the top`() = runTest { … }

@Test
fun `pinned notes sort above recently edited ones`() = runTest {
    val old = repository.create(); val recent = repository.create()
    repository.setPinned(old, true)
    assertEquals(listOf(old, recent), repository.observeAll().first().map { it.id })
}

@Test
fun `a soft-deleted note leaves the list and the index`() = runTest { … }

@Test
fun `the indexed text is the text blocks, and an image contributes nothing`() = runTest {
    val id = repository.create()
    repository.save(id, NoteDocument(listOf(
        Block.Text("b1", text = "kacchi at Sultans"),
        Block.Image("b2", "abc123", 10, 10),
    )))
    val text = source.textFor(id)!!
    assertTrue(text.contains("kacchi"))
    assertFalse(text.contains("abc123"))
}

@Test
fun `a note is findable by a word in its body`() = runTest {
    val id = repository.create()
    repository.save(id, NoteDocument(listOf(Block.Text("b1", text = "passport renewal"))))
    assertEquals(listOf(id), db.searchDao().idsMatching(ENTITY_NOTE, ftsQuery("passport")!!))
}
```

- [ ] **Step 2: Implement, run, pass**

`save` encodes the document, stamps `updatedAt`, and reindexes. `create` inserts
`NoteDocument.empty()`. Bind `NoteIndexSource` `@IntoSet` beside the other five and add it to
`TestSearchIndex`.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(notes): saving without being asked, and findable afterwards"
```

---

### Task 4: The ruled page and the editor

**Files:**
- Create: `app/src/main/java/com/wasif/khata/feature/notes/RuledPage.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/notes/NoteEditorScreen.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/notes/NoteEditorUiState.kt`
- Create: `app/src/main/java/com/wasif/khata/feature/notes/NoteEditorViewModel.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/notes/NoteEditorViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/notes/NoteEditorScreenTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–3, `MediaStore`, `FieldScaffold`, `Pill`, `NavCircle`.
- Produces: `NoteEditorUiState(document, focusedBlockId, selection, pendingMarks, saving)`,
  `NoteEditorActions`, `RuledPage(ruleSpacing) { … }`.

- [ ] **Step 1: Build `RuledPage`**

A `Canvas` **inside** the scrolling content — not behind the viewport — drawing a horizontal rule
every `ruleSpacing` and one vertical margin rule in the accent at low alpha. It takes a list of
"skip" ranges so no rules are drawn behind image blocks.

```kotlin
// Derived, never hardcoded: at a 1.3x font scale a fixed dp rule spacing leaves every line
// floating between rules instead of resting on one.
val ruleSpacing = with(LocalDensity.current) {
    MaterialTheme.typography.bodyLarge.lineHeight.toDp()
}
```

- [ ] **Step 2: Write the failing view-model tests**

```kotlin
@Test fun `enter mid-line splits the block and moves focus to the new one`() { … }
@Test fun `enter on an empty bullet ends the list rather than adding another`() { … }
@Test fun `backspace at the start of a bullet converts it to a paragraph first`() { … }
@Test fun `backspace at the start of a paragraph merges it into the block above`() { … }
@Test fun `backspace before an image selects the image rather than swallowing it`() { … }
@Test fun `bold with a selection marks it, and with a caret arms the next run typed`() { … }
@Test fun `autosave writes once per debounce, not once per keystroke`() { … }
@Test fun `an undecodable image inserts nothing and leaves no empty block`() { … }
```

- [ ] **Step 3: Build the editor**

One `BasicTextField` per text block, keyed by block id, each with its own `FocusRequester`.
Image blocks render through Coil with a selected state. `Modifier.contentReceiver` on the text
fields catches pasted and keyboard-inserted images. The toolbar sits above the keyboard
(`imePadding`), in the bottom third by construction.

- [ ] **Step 4: Run, then commit**

```bash
git commit -m "feat(notes): a page with lines on it, and blocks that split and merge"
```

---

### Task 5: The list, the hub tile, the share target, and search

**Files:**
- Create: `NotesScreen.kt`, `NotesUiState.kt`, `NotesViewModel.kt` in `feature/notes`
- Modify: `KhataNavHost.kt`, `ModulesScreen.kt`, `MainActivity.kt` + `AndroidManifest.xml`
  (`ACTION_SEND` image), `SearchUiState.kt`, `SearchViewModel.kt`, `SearchScreen.kt`
- Test: `app/src/test/java/com/wasif/khata/feature/notes/NotesViewModelTest.kt`
- Test: `app/src/androidTest/java/com/wasif/khata/feature/notes/NotesScreenTest.kt`

- [ ] **Step 1: The list** — derived title, snippet, date, pin; pinned first. Empty state says so.
- [ ] **Step 2: Sixth search group**, resolving `ENTITY_NOTE` ids through `noteDao.findByIds`.
- [ ] **Step 3: The share target** — `ACTION_SEND` with an `image/*` mime opens a new note holding
      that image, beside the existing `text/plain` Maps target. The manifest gains one filter.
- [ ] **Step 4: Wake the hub tile.** Notes was the last dormant one, so `DormantTile` and its
      "Not built" copy lose their final caller — delete both rather than leave dead code.
- [ ] **Step 5: Run everything, then commit**

```bash
git commit -m "feat(notes): the last tile wakes up"
```

---

## Self-Review

**Spec coverage:** §3 model → Task 1. §4 editor → Tasks 2 and 4. §5 ruled page → Task 4. §6
screens → Tasks 4–5. §7 search → Tasks 3 and 5. §8 testing → each task's test step. §9 out of
scope → nothing here builds undo, folders, tags, tables, camera capture, or reminders.

**Types:** `Block`, `TextKind`, `Mark`, `Span`, `NoteDocument`, `NoteJson`, `NoteEntity`,
`NoteDao`, `ENTITY_NOTE`, `applyEdit`, `diffRange`, `toggleMark`, `splitBlock`, `mergeBlocks`,
`numberFor`, `titleOf`, `snippetOf`, `rulesFor` are defined in Tasks 1–2 and used under those
exact names afterwards.

**Known risk, stated rather than discovered:** `kotlinx.serialization` throws on an unregistered
class discriminator even with `ignoreUnknownKeys`, so Task 1 Step 2's "unknown block kind" test
may fail against the straightforward implementation. The fix is in that step: decode element by
element and drop what fails. The test stays; the implementation changes.
