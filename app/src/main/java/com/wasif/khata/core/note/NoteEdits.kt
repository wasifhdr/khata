package com.wasif.khata.core.note

/**
 * Every edit a note can receive, as pure functions. The editor screen decides *when*; this file
 * decides *what*, so the logic that silently corrupts a note is testable without a screen.
 */

/**
 * Maps spans through one replaced range. Every edit reduces to that, so there are three cases
 * and no more: wholly before is untouched, wholly after shifts by the delta, overlapping is
 * clipped. A span clipped to nothing is dropped rather than kept as an empty range.
 */
fun applyEdit(spans: List<Span>, replaced: IntRange, insertedLength: Int): List<Span> {
    val from = replaced.first
    val to = from + replaced.count()
    val delta = insertedLength - replaced.count()

    return spans.mapNotNull { span ->
        val start = when {
            span.start < from -> span.start
            span.start >= to -> span.start + delta
            // Started inside what was replaced: resumes after whatever was inserted.
            else -> from + insertedLength
        }
        val end = when {
            // Ended exactly where the edit began, so typing there is outside the mark --
            // which is what the pending mark in the editor exists to override.
            span.end <= from -> span.end
            span.end >= to -> span.end + delta
            else -> from
        }
        if (end > start) span.copy(start = start, end = end) else null
    }
}

/**
 * Adds the mark over [range], or removes it if every character in the range already carries it.
 * Toggling is what a formatting button does, and asking twice must undo rather than stack.
 */
fun toggleMark(spans: List<Span>, range: IntRange, mark: Mark): List<Span> {
    val from = range.first
    val to = from + range.count()
    if (to <= from) return spans

    val others = spans.filterNot { it.mark == mark }
    val mine = spans.filter { it.mark == mark }

    val covered = (from until to).all { i -> mine.any { i >= it.start && i < it.end } }

    val changed = if (covered) {
        // Remove: keep whatever lies outside the range, which may leave one span on each side.
        mine.flatMap { span ->
            listOfNotNull(
                span.takeIf { it.start < from }?.copy(end = minOf(span.end, from)),
                span.takeIf { it.end > to }?.copy(start = maxOf(span.start, to)),
            )
        }
    } else {
        mine + Span(from, to, mark)
    }

    return (others + merge(changed)).sortedWith(compareBy({ it.start }, { it.mark }))
}

/**
 * Joins spans of one mark that touch or overlap. Without this a long editing session grows a
 * span per keystroke: the note looks identical on screen while its JSON balloons.
 */
private fun merge(spans: List<Span>): List<Span> {
    if (spans.isEmpty()) return spans
    val sorted = spans.filter { it.end > it.start }.sortedBy { it.start }
    val out = mutableListOf<Span>()
    sorted.forEach { span ->
        val last = out.lastOrNull()
        if (last != null && last.mark == span.mark && span.start <= last.end) {
            out[out.lastIndex] = last.copy(end = maxOf(last.end, span.end))
        } else {
            out += span
        }
    }
    return out
}

/**
 * A span pointing outside its text survived some earlier bad write or a hand-edited backup.
 * Clamped on load rather than trusted, because drawing one throws while rendering.
 */
fun clampSpans(spans: List<Span>, textLength: Int): List<Span> = spans.mapNotNull { span ->
    val start = span.start.coerceIn(0, textLength)
    val end = span.end.coerceIn(0, textLength)
    if (end > start) span.copy(start = start, end = end) else null
}

/**
 * Reduces a text change to one replaced range by trimming the common prefix and suffix. Compose
 * hands over only the old and new strings, so this is how an edit becomes something spans can be
 * mapped through. It cannot tell a paste from a type, and does not need to.
 */
fun diffRange(old: String, new: String): Pair<IntRange, Int> {
    var prefix = 0
    val maxPrefix = minOf(old.length, new.length)
    while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++

    var suffix = 0
    while (
        suffix < maxPrefix - prefix &&
        old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
    ) {
        suffix++
    }

    val removedEnd = old.length - suffix
    return (prefix until removedEnd) to (new.length - suffix - prefix)
}

/**
 * Splits at the cursor: text before stays, text after becomes the block below, spans partitioned
 * across the cut. A heading splits into a paragraph -- you are done with the heading -- while a
 * list item continues its list, which is what makes Enter feel like a list.
 */
fun splitBlock(block: Block.Text, offset: Int, newId: String): Pair<Block.Text, Block.Text> {
    val at = offset.coerceIn(0, block.text.length)

    val first = block.copy(
        text = block.text.take(at),
        spans = clampSpans(block.spans, at),
    )
    val second = Block.Text(
        id = newId,
        kind = if (block.kind == TextKind.HEADING) TextKind.PARAGRAPH else block.kind,
        text = block.text.drop(at),
        spans = clampSpans(
            block.spans.map { it.copy(start = it.start - at, end = it.end - at) },
            block.text.length - at,
        ),
        // A new check item starts unchecked, however the one above it was left.
        checked = false,
    )
    return first to second
}

/**
 * Merges [second] into [first], shifting the incoming spans past the existing text. The result
 * keeps the first block's kind: backspacing a paragraph into a bullet joins the bullet.
 */
fun mergeBlocks(first: Block.Text, second: Block.Text): Block.Text {
    val offset = first.text.length
    return first.copy(
        text = first.text + second.text,
        spans = merge(
            first.spans + second.spans.map { it.copy(start = it.start + offset, end = it.end + offset) },
        ).sortedWith(compareBy({ it.start }, { it.mark })),
    )
}

/**
 * The number a list item shows, counted from its contiguous siblings. Nothing is stored, so
 * inserting a line renumbers nothing -- it simply counts differently next time it draws.
 */
fun numberFor(blocks: List<Block>, index: Int): Int? {
    val block = blocks.getOrNull(index) as? Block.Text ?: return null
    if (block.kind != TextKind.NUMBERED) return null

    var number = 1
    var i = index - 1
    while (i >= 0) {
        val previous = blocks[i] as? Block.Text ?: break
        if (previous.kind != TextKind.NUMBERED) break
        number++
        i--
    }
    return number
}

/** The list's title: the first block with text in it. An image-first note has none. */
fun titleOf(document: NoteDocument): String? = document.blocks
    .filterIsInstance<Block.Text>()
    .firstOrNull { it.text.isNotBlank() }
    ?.text
    ?.trim()

/** What follows the title, for the second line of a row. */
fun snippetOf(document: NoteDocument): String? = document.blocks
    .filterIsInstance<Block.Text>()
    .filter { it.text.isNotBlank() }
    .drop(1)
    .firstOrNull()
    ?.text
    ?.trim()

/** The plain text a note is searched by. Images contribute nothing: a photo has no words. */
fun plainTextOf(document: NoteDocument): String = document.blocks
    .filterIsInstance<Block.Text>()
    .map { it.text }
    .filter { it.isNotBlank() }
    .joinToString(" ")

/**
 * Puts an image after the block the caret was in, with an empty paragraph beneath it so there
 * is somewhere to keep typing. With no focused block it goes at the end, which is where a
 * picture shared into a note belongs.
 */
fun insertImageAfter(
    blocks: List<Block>,
    focusedBlockId: String?,
    image: Block.Image,
    newParagraphId: String,
): List<Block> {
    val at = blocks.indexOfFirst { it.id == focusedBlockId }.takeIf { it >= 0 } ?: blocks.lastIndex
    return blocks.toMutableList().apply {
        addAll(at + 1, listOf(image, Block.Text(id = newParagraphId)))
    }
}

/**
 * How many rules an image occupies, rounded up. A picture that ends part-way through a rule
 * pushes every line beneath it off the grid, which is the one thing the ruled page cannot do.
 */
fun rulesFor(heightPx: Int, ruleSpacingPx: Int): Int {
    if (ruleSpacingPx <= 0) return 0
    return (heightPx + ruleSpacingPx - 1) / ruleSpacingPx
}

/** What the hub tile shows: derived from the notes themselves, so nothing is stored twice. */
data class NotesStats(val lastTitle: String?, val total: Int, val toDo: Int)

/**
 * [contents] is every live note's JSON, newest first. The title comes from the newest note and
 * the to-do count from every unchecked checkbox across all of them -- the one number on the hub
 * that asks you to do something rather than telling you what you already did.
 */
fun notesStats(contents: List<String>, newId: () -> String): NotesStats {
    val documents = contents.map { NoteJson.decode(it, newId) }
    return NotesStats(
        lastTitle = documents.firstOrNull()?.let(::titleOf),
        total = documents.size,
        toDo = documents.sumOf { document ->
            document.blocks
                .filterIsInstance<Block.Text>()
                .count { it.kind == TextKind.CHECK && !it.checked && it.text.isNotBlank() }
        },
    )
}
