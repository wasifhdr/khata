package com.wasif.khata.core.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json, which is a platform class rather than a JVM one -- the same
// reason GeminiResponseTest and TmdbParseTest need it.
@RunWith(RobolectricTestRunner::class)
class BlockJsonTest {

    private var n = 0
    private fun id() = "gen${n++}"

    @Test
    fun `a document round-trips through json`() {
        val document = NoteDocument(
            listOf(
                Block.Text("b1", TextKind.HEADING, "Shopping"),
                Block.Text("b2", TextKind.CHECK, "Rice", listOf(Span(0, 4, Mark.BOLD)), checked = true),
                Block.Image("b3", "abc123", 800, 600),
            ),
        )

        assertEquals(document.blocks, NoteJson.decode(NoteJson.encode(document), ::id).blocks)
    }

    @Test
    fun `an unknown block kind costs its own content and not the rest of the note`() {
        // A block kind from a later version, arriving on a downgrade or a partial restore.
        val raw = """
            [{"type":"text","id":"b1","text":"kept"},
             {"type":"sketch","id":"b2"},
             {"type":"text","id":"b3","text":"also kept"}]
        """.trimIndent()

        val texts = NoteJson.decode(raw, ::id).blocks.filterIsInstance<Block.Text>().map { it.text }

        assertEquals(listOf("kept", "also kept"), texts)
    }

    @Test
    fun `an unreadable block keeps whatever text it carried`() {
        val raw = """[{"type":"sketch","id":"b1","text":"a caption worth keeping"}]"""

        val block = NoteJson.decode(raw, ::id).blocks.single() as Block.Text

        assertEquals("a caption worth keeping", block.text)
    }

    @Test
    fun `content that is not json at all opens as one paragraph holding it`() {
        val document = NoteJson.decode("not json {", ::id)

        assertEquals("not json {", (document.blocks.single() as Block.Text).text)
    }

    @Test
    fun `an empty document is one empty paragraph, never zero blocks`() {
        val blocks = NoteJson.decode("[]", ::id).blocks

        assertEquals(1, blocks.size)
        assertTrue((blocks.single() as Block.Text).text.isEmpty())
    }
}
