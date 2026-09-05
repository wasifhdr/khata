# Khata (খাতা) — Design Spec: The Notes Module

**Date:** 2026-09-05
**Status:** Design decided. Implementation plan written; not yet built.
**Requires:** `2026-09-04-khata-shared-spine-design.md` — media and search. Builds on `main` at
schema 13 (restaurants 10→11, car servicing 11→12, watchlist 12→13); notes take **13 → 14**.
**Settles:** the last dormant tile on the hub, and the "notes with inline images" named as a
Phase 2 candidate in `2026-08-26-khata-wallet-design.md` §14.

---

## 1. What this is

The fifth module: a place for notes, written on something that behaves like a notepad. Rich
text — bold, italic, strikethrough, headings, bullets, numbered lists, checkboxes — with
pictures sitting inline between the lines, on a ruled page.

It is the first module whose difficulty is entirely in the editor. The other four are forms over
tables; this one is a document editor, and **Compose has no rich-text editor**. `BasicTextField`
edits plain text. Every mark, every block boundary, and every picture between two lines is
built here. That is the whole cost of the module, and it is worth naming before the schema,
because the schema is trivial by comparison.

---

## 2. Decisions taken

Settled during design. Not open for re-litigation during implementation.

| # | Decision | Rationale |
|---|---|---|
| N1 | A note is an ordered list of **blocks**, stored as JSON in one column | Inline images between lines make a note a document, not a string. One row per note, one write per save, and reordering is a list operation rather than a renumbering of sibling rows |
| N2 | **No title column.** The list's title is the first non-empty block's text | A title field is a second thing to fill in for something already typed. Derived, so it cannot go stale |
| N3 | Every block carries a stable **`id`** | Focus, list keys and animations all need identity that survives an insert two blocks above. Index-based identity breaks on the first split |
| N4 | Numbered-list numbers are **computed at render** | Store the number and inserting a line renumbers the file. Nothing stored is nothing to drift |
| N5 | An image block carries the **content hash**, and the note also gets a `media_links` row | The hash draws the picture; the link is what backup, deduplication and "files are never deleted" already understand (spine S5, S6) |
| N6 | `pinned` is a column; ordering is `pinned DESC, updatedAt DESC` | One boolean, one sort clause |
| N7 | **Autosave on a debounce.** No save button; delete is explicit and soft | Principle 1. A note you must remember to save is a note you eventually lose |
| N8 | No folders, no notebooks, no tags | The user's call: search plus pinning. A folder you must file into is the chore principle 1 warns about |
| N9 | The editor is **hand-rolled**, not a rich-text library | §4 |
| N10 | Marks apply to a selection; with a collapsed cursor a **pending mark** applies to the next run typed | The flow everyone expects from a notepad, for one nullable field of state |
| N11 | Every vertical dimension is a whole number of **rules** | §5. The grid is the feature; a block with arbitrary padding breaks the page from that point down |
| N12 | **Cross-block undo is out of scope.** Within a block, `BasicTextField` provides it free | Undoing a split or a merge needs an editor-wide command stack, which is a module of its own |

---

## 3. Data model

Migration **13 → 14**, hand-written SQL matching the existing ones, with a `Migration13To14Test`.
`BackupRepository.SCHEMA_VERSION` goes to 14 with it, for the reason the vehicle spec §3 gives.

House quartet. Deletes are soft.

```sql
notes (id, uuid, content TEXT, pinned INTEGER NOT NULL DEFAULT 0, …)
```

Index beyond `uuid`: `notes(updatedAt)`.

`content` is the block list, serialised with `kotlinx.serialization` — already in the stack, so
no dependency arrives with it:

```kotlin
@Serializable
sealed interface Block {
    val id: String

    @Serializable @SerialName("text")
    data class Text(
        override val id: String,
        val kind: TextKind,                   // PARAGRAPH, HEADING, BULLET, NUMBERED, CHECK
        val text: String,
        val spans: List<Span> = emptyList(),
        /** Meaningful only for CHECK. A paragraph is never "unchecked". */
        val checked: Boolean = false,
    ) : Block

    @Serializable @SerialName("image")
    data class Image(
        override val id: String,
        val sha256: String,
        val widthPx: Int,
        val heightPx: Int,
    ) : Block
}

@Serializable
data class Span(val start: Int, val end: Int, val mark: Mark)   // BOLD, ITALIC, STRIKE
```

The `Json` instance sets **`ignoreUnknownKeys = true`**, so a block kind added in a later version
does not make every existing note unreadable on a downgrade or a partial restore. A note whose
JSON cannot be parsed at all opens as a single paragraph holding the raw text rather than an
empty page — principle 3: nothing is ever lost, including a note this module fails to understand.

**The derived pieces**, none stored:

- **Title** — the first non-empty `Text` block. A note that opens with an image is "Untitled".
- **Snippet** — the next non-empty text after the title.
- **List numbering** — the count of contiguous preceding `NUMBERED` siblings (N4).

---

## 4. The editor

### Why it is hand-rolled

Three approaches were weighed. A Compose rich-text library solves inline marks and hands over a
state holder; a `WebView` with `contenteditable` solves everything at once. Both were declined:

- The **WebView** cannot be themed with `KhataTheme`, cannot sit on the ruled grid, and would
  round-trip the document through HTML — three problems traded for one.
- A **library** solves the span bookkeeping, which is roughly half the difficulty. It does not
  solve pictures between lines, which is the half that was actually asked for, because that needs
  block plumbing either way. One dependency that removes 40% of the hard part is not worth a
  second UI dialect in an app whose stack is fixed.

If the span bookkeeping proves worse than it looks, a library is a contained retreat: the JSON
document and the ruled grid do not change.

### Shape

One `BasicTextField` per text block, inside the scrolling page. **Image blocks are not text
fields** — they are pictures with a selected state.

### The four boundary operations

These are the editor, and they are where editors get buggy.

| Gesture | Behaviour |
|---|---|
| **Enter** mid-line | Split at the cursor: text before stays, text after becomes a new block below, spans partitioned across the cut. Focus follows to offset 0 of the new block. A heading splits into a paragraph |
| **Enter** on an empty `BULLET`, `NUMBERED` or `CHECK` | Ends the list: the block becomes a `PARAGRAPH` rather than spawning another empty item |
| **Backspace** at offset 0 of a non-paragraph | Converts the block to a `PARAGRAPH`. Leaving a list deletes nothing |
| **Backspace** at offset 0 of a paragraph | Merges into the previous text block — append, shift the incoming spans by the previous text's length, focus at the join. If the previous block is an **image**, the image is selected instead of swallowed; one more press deletes it |

### Span bookkeeping

The piece that silently corrupts a note if it is wrong, so it is a pure function tested on its
own. Every edit is reduced to a single replaced range by trimming the common prefix and suffix,
then each span is mapped through it:

```kotlin
fun applyEdit(spans: List<Span>, replaced: IntRange, insertedLength: Int): List<Span>
```

Spans wholly before the replacement are untouched; spans wholly after shift by the delta; spans
overlapping it are clipped, and a span clipped to nothing is dropped. Adjacent spans of the same
mark are merged, so repeated editing cannot grow an unbounded list of one-character spans.

### Pictures

Three entry points, one destination:

- **Paste**, via `Modifier.contentReceiver` (Compose foundation 1.10.6, already on the classpath),
  which catches both a pasted image and one inserted by the keyboard — the clipboard path,
  without reading the clipboard by hand.
- **The gallery picker**, the same `PickMultipleVisualMedia` contract the other modules use.
- **`ACTION_SEND` of an image**, landing in a new note, beside the existing Maps share target.

All three go through `MediaStore.import` / `importBytes` — content-addressed, write-once,
deduplicated — then insert an `Image` block at the cursor, splitting the current paragraph around
it. **An image that will not decode inserts nothing and says so**, rather than leaving an empty
block behind.

Camera capture is out of scope: it is the one entry point that adds a permission.

---

## 5. The ruled page

The rhythm is **derived from the text**, not hardcoded: `ruleSpacing` is the body style's
measured line height converted to dp. A fixed `28.dp` looks right at a 1.0× font scale and is
visibly wrong at 1.3× — which is the accessibility setting most likely to be switched on.

Everything vertical is then a whole number of rules:

| Element | On the grid |
|---|---|
| Paragraph, bullet, numbered, check | Line height **= 1 rule** |
| Heading | Line box **= 2 rules** — bigger type, still landing on a rule |
| Image | Height rounded **up** to a whole number of rules, so the page resumes in rhythm |
| Between blocks | **No padding at all.** A gap that is not a multiple of a rule breaks the page from there down |

**The rules are drawn inside the scrolling content, not behind the viewport.** Painted on the
viewport they would sit still while the text scrolled past them, and the alignment would only be
correct at the top of the page — precisely what makes fake paper look fake.

No rules are drawn under an image: paper is covered where the photo sits.

**A left margin rule** runs the height of the page in the pale-aqua accent at low alpha — the
vertical line a notepad has, and also the gutter where bullets, numbers and checkboxes sit, so it
earns its place twice.

**On contrast:** the rules are deliberately low-contrast against the teal-black ground. This does
not conflict with the product's bright-sunlight requirement, which governs text that carries
meaning. A rule carries none, and a rule as legible as the writing is a worksheet.

Bengali script must render correctly in note text, as everywhere else — and, unlike elsewhere,
it must also sit on the rules, so the line-height lock is checked against Bengali glyphs, whose
ascenders and descenders are taller than the Latin ones.

---

## 6. Screens

Package `feature/notes`, package-per-feature (D2).

| Screen | Content |
|---|---|
| **Notes** (hub tile) | Pinned first, then most recently edited: derived title, a one-line snippet, the date, a pin. Primary action in the bottom third: **New note** |
| **Note** | The ruled page, autosaving. A toolbar above the keyboard: bold · italic · strike · heading · bullet · numbered · check · image. Back leaves; the trash icon soft-deletes |

There is no view mode. The editor is the note, which is the point of editing live.

---

## 7. Search

One `IndexSource`, `entityType = "note"`, whose text is the concatenation of the note's text
blocks. A hit opens the note.

Image blocks contribute nothing: a photograph has no text to find it by, and the alternative —
indexing a file name — would return notes for a search that matched a hash.

Sixth group on the global search screen. Kind then recency, as everywhere.

---

## 8. Testing

Concentrated where a failure would be silent rather than loud.

- **`Migration13To14Test`** — the table and its index.
- **`applyEdit`** — insertion before, inside and after a span; a deletion clipping one; a
  replacement spanning two; a span clipped to nothing dropped; adjacent same-mark spans merged.
- **Split** partitions spans at the cut; **merge** shifts the second block's spans by the first's
  length; Backspace on a bullet converts before it merges; Enter on an empty bullet ends the list.
- **JSON round-trip**, including a document holding an unknown block kind, and a `content` that
  is not JSON at all opening as one paragraph rather than an empty note.
- **Autosave** writes once per debounce, not once per keystroke.
- **Image insert** splits the paragraph at the cursor; an undecodable image inserts nothing.
- **Derived title and snippet**, including a note that opens with an image.
- **Grid arithmetic** — an image's height rounds up to a whole number of rules.
- **Screens** — list ordering with pinned first, and the editor rendering each block kind.

---

## 9. Out of scope

- **Cross-block undo and redo** (N12).
- **Folders, notebooks, tags, colours and highlighting** (N8).
- **Tables, code blocks, quotes, and links as marks.**
- **Drawing, handwriting and voice notes.**
- **Camera capture** into a note (§4).
- **Reminders and due dates** — a checkbox here is a list item, not a deadline. Deadlines remain
  a separate candidate module.
- **Note-to-note linking, version history, export, and sharing a note out of the app.**
