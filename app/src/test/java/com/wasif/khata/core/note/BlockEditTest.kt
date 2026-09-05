package com.wasif.khata.core.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun `a span entirely after the cut moves to the second block`() {
        val block = Block.Text("b1", text = "hello world", spans = listOf(Span(6, 11, Mark.ITALIC)))

        val (first, second) = splitBlock(block, offset = 5, newId = "b2")

        assertEquals(emptyList<Span>(), first.spans)
        assertEquals(listOf(Span(1, 6, Mark.ITALIC)), second.spans)
    }

    @Test
    fun `splitting a heading leaves a paragraph below it`() {
        val (first, second) = splitBlock(Block.Text("b1", TextKind.HEADING, "Title"), 5, "b2")

        assertEquals(TextKind.HEADING, first.kind)
        assertEquals(TextKind.PARAGRAPH, second.kind)
    }

    @Test
    fun `splitting a bullet continues the list`() {
        assertEquals(
            TextKind.BULLET,
            splitBlock(Block.Text("b1", TextKind.BULLET, "milk"), 4, "b2").second.kind,
        )
    }

    @Test
    fun `a new check item starts unchecked however the one above was left`() {
        val done = Block.Text("b1", TextKind.CHECK, "rice", checked = true)

        val (first, second) = splitBlock(done, 4, "b2")

        assertEquals(true, first.checked)
        assertEquals(false, second.checked)
    }

    @Test
    fun `merging shifts the second block's spans past the first's text`() {
        val a = Block.Text("b1", text = "hello")
        val b = Block.Text("b2", text = " world", spans = listOf(Span(1, 6, Mark.ITALIC)))

        val merged = mergeBlocks(a, b)

        assertEquals("hello world", merged.text)
        assertEquals(listOf(Span(6, 11, Mark.ITALIC)), merged.spans)
    }

    @Test
    fun `merging keeps the first block's kind, so a paragraph joins the bullet above it`() {
        val bullet = Block.Text("b1", TextKind.BULLET, "milk")
        val paragraph = Block.Text("b2", TextKind.PARAGRAPH, " and eggs")

        assertEquals(TextKind.BULLET, mergeBlocks(bullet, paragraph).kind)
    }

    @Test
    fun `merging two marked runs that meet leaves one span, not two`() {
        val a = Block.Text("b1", text = "hello", spans = listOf(Span(0, 5, Mark.BOLD)))
        val b = Block.Text("b2", text = " there", spans = listOf(Span(0, 6, Mark.BOLD)))

        assertEquals(listOf(Span(0, 11, Mark.BOLD)), mergeBlocks(a, b).spans)
    }

    @Test
    fun `numbering is derived from contiguous siblings, so inserting renumbers nothing`() {
        val blocks = listOf(
            Block.Text("b1", TextKind.NUMBERED, "one"),
            Block.Text("b2", TextKind.NUMBERED, "two"),
            Block.Text("b3", TextKind.PARAGRAPH, "an aside"),
            Block.Text("b4", TextKind.NUMBERED, "restarts"),
        )

        assertEquals(1, numberFor(blocks, 0))
        assertEquals(2, numberFor(blocks, 1))
        assertNull(numberFor(blocks, 2))
        assertEquals(1, numberFor(blocks, 3))
    }

    @Test
    fun `an image between two numbered items breaks the run`() {
        val blocks = listOf(
            Block.Text("b1", TextKind.NUMBERED, "one"),
            Block.Image("b2", "abc", 10, 10),
            Block.Text("b3", TextKind.NUMBERED, "one again"),
        )

        assertEquals(1, numberFor(blocks, 2))
    }

    @Test
    fun `the title is the first block with text in it`() {
        val document = NoteDocument(
            listOf(
                Block.Text("b1", text = "   "),
                Block.Text("b2", text = "Shopping"),
                Block.Text("b3", text = "rice"),
            ),
        )

        assertEquals("Shopping", titleOf(document))
        assertEquals("rice", snippetOf(document))
    }

    @Test
    fun `a note that opens with an image has no title`() {
        assertNull(titleOf(NoteDocument(listOf(Block.Image("b1", "abc", 10, 10)))))
    }

    @Test
    fun `the searched text is the text blocks, and an image contributes nothing`() {
        val document = NoteDocument(
            listOf(
                Block.Text("b1", text = "kacchi at Sultans"),
                Block.Image("b2", "abc123def", 10, 10),
                Block.Text("b3", text = "ask Rafi"),
            ),
        )

        assertEquals("kacchi at Sultans ask Rafi", plainTextOf(document))
    }

    @Test
    fun `an image lands after the focused block, with somewhere to type beneath it`() {
        val blocks = listOf(Block.Text("b1", text = "above"), Block.Text("b2", text = "below"))

        val result = insertImageAfter(blocks, "b1", Block.Image("img", "abc", 10, 10), "new")

        assertEquals(listOf("b1", "img", "new", "b2"), result.map { it.id })
        assertEquals("", (result[2] as Block.Text).text)
    }

    @Test
    fun `an image with nothing focused goes at the end, where a shared picture belongs`() {
        val blocks = listOf(Block.Text("b1", text = "above"))

        val result = insertImageAfter(blocks, null, Block.Image("img", "abc", 10, 10), "new")

        assertEquals(listOf("b1", "img", "new"), result.map { it.id })
    }

    @Test
    fun `an image occupies a whole number of rules`() {
        assertEquals(3, rulesFor(heightPx = 200, ruleSpacingPx = 70))
        assertEquals(2, rulesFor(heightPx = 140, ruleSpacingPx = 70))
        assertEquals(1, rulesFor(heightPx = 1, ruleSpacingPx = 70))
        // Before the page has been measured there is no rhythm to round to.
        assertEquals(0, rulesFor(heightPx = 200, ruleSpacingPx = 0))
    }
}
