package com.wasif.khata.core.note

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanEditTest {

    private val bold = Mark.BOLD
    private val italic = Mark.ITALIC

    @Test
    fun `an insertion before a span shifts it`() {
        assertEquals(
            listOf(Span(8, 13, bold)),
            applyEdit(listOf(Span(5, 10, bold)), replaced = 0 until 0, insertedLength = 3),
        )
    }

    @Test
    fun `an insertion inside a span extends it`() {
        assertEquals(
            listOf(Span(0, 8, bold)),
            applyEdit(listOf(Span(0, 5, bold)), replaced = 2 until 2, insertedLength = 3),
        )
    }

    @Test
    fun `an insertion after a span leaves it alone`() {
        assertEquals(
            listOf(Span(0, 5, bold)),
            applyEdit(listOf(Span(0, 5, bold)), replaced = 7 until 7, insertedLength = 3),
        )
    }

    @Test
    fun `a deletion overlapping the end of a span clips it`() {
        assertEquals(
            listOf(Span(2, 5, bold)),
            applyEdit(listOf(Span(2, 8, bold)), replaced = 5 until 8, insertedLength = 0),
        )
    }

    @Test
    fun `a deletion overlapping the start of a span clips and shifts it`() {
        assertEquals(
            listOf(Span(2, 5, bold)),
            applyEdit(listOf(Span(4, 8, bold)), replaced = 2 until 5, insertedLength = 0),
        )
    }

    @Test
    fun `a span clipped to nothing is dropped, not kept as an empty range`() {
        assertEquals(
            emptyList<Span>(),
            applyEdit(listOf(Span(2, 5, bold)), replaced = 2 until 5, insertedLength = 0),
        )
    }

    @Test
    fun `a replacement spanning two spans clips both`() {
        assertEquals(
            listOf(Span(0, 3, bold), Span(4, 7, bold)),
            applyEdit(
                listOf(Span(0, 4, bold), Span(6, 10, bold)),
                replaced = 3 until 7,
                insertedLength = 1,
            ),
        )
    }

    @Test
    fun `marks of different kinds do not interfere`() {
        assertEquals(
            listOf(Span(3, 8, bold), Span(3, 6, italic)),
            applyEdit(
                listOf(Span(0, 5, bold), Span(0, 3, italic)),
                replaced = 0 until 0,
                insertedLength = 3,
            ),
        )
    }

    @Test
    fun `toggling a mark over a fresh range adds it`() {
        assertEquals(listOf(Span(2, 6, bold)), toggleMark(emptyList(), 2 until 6, bold))
    }

    @Test
    fun `adjacent spans of the same mark merge, so editing cannot grow an unbounded list`() {
        assertEquals(listOf(Span(0, 6, bold)), toggleMark(listOf(Span(0, 3, bold)), 3 until 6, bold))
    }

    @Test
    fun `overlapping spans of the same mark merge rather than stacking`() {
        assertEquals(listOf(Span(0, 8, bold)), toggleMark(listOf(Span(0, 5, bold)), 3 until 8, bold))
    }

    @Test
    fun `toggling a range that already carries the mark removes it`() {
        assertEquals(emptyList<Span>(), toggleMark(listOf(Span(0, 6, bold)), 0 until 6, bold))
    }

    @Test
    fun `removing the middle of a span leaves the two ends`() {
        assertEquals(
            listOf(Span(0, 2, bold), Span(4, 6, bold)),
            toggleMark(listOf(Span(0, 6, bold)), 2 until 4, bold),
        )
    }

    @Test
    fun `spans are clamped to the text they belong to`() {
        // A span pointing past the end survived some earlier bad write; drawing it would throw.
        assertEquals(listOf(Span(0, 4, bold)), clampSpans(listOf(Span(0, 99, bold)), textLength = 4))
        assertEquals(emptyList<Span>(), clampSpans(listOf(Span(7, 9, bold)), textLength = 4))
    }

    @Test
    fun `diffRange finds the changed range by trimming both ends`() {
        val (replaced, inserted) = diffRange("hello world", "hello brave world")
        assertEquals(6 until 6, replaced)
        assertEquals(6, inserted)
    }

    @Test
    fun `diffRange on a deletion reports the removed range and nothing inserted`() {
        val (replaced, inserted) = diffRange("hello world", "hello")
        assertEquals(5 until 11, replaced)
        assertEquals(0, inserted)
    }

    @Test
    fun `diffRange on no change reports an empty edit`() {
        val (replaced, inserted) = diffRange("same", "same")
        assertEquals(0, replaced.last - replaced.first + 1)
        assertEquals(0, inserted)
    }
}
