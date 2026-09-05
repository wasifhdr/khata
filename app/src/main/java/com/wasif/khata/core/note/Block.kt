package com.wasif.khata.core.note

import org.json.JSONArray
import org.json.JSONObject

enum class TextKind { PARAGRAPH, HEADING, BULLET, NUMBERED, CHECK }

enum class Mark { BOLD, ITALIC, STRIKE }

/** Half-open: [start, end). An empty span is never stored -- see NoteEdits.applyEdit. */
data class Span(val start: Int, val end: Int, val mark: Mark)

sealed interface Block {
    val id: String

    data class Text(
        override val id: String,
        val kind: TextKind = TextKind.PARAGRAPH,
        val text: String = "",
        val spans: List<Span> = emptyList(),
        /** Meaningful only for CHECK: a paragraph is never "unchecked". */
        val checked: Boolean = false,
    ) : Block

    data class Image(
        override val id: String,
        /** The content hash, which is also the file name in the media store. */
        val sha256: String,
        val widthPx: Int,
        val heightPx: Int,
    ) : Block
}

/**
 * A note's whole content. An empty document is one empty paragraph rather than zero blocks:
 * a note with no blocks has nowhere to put the cursor.
 */
data class NoteDocument(val blocks: List<Block>) {
    companion object {
        fun empty(newId: () -> String) = NoteDocument(listOf(Block.Text(id = newId())))
    }
}

/**
 * org.json, like every other payload this app reads -- Gemini, TMDB and Drive all parse by hand
 * here. kotlinx.serialization would mean a library and a compiler plugin to write a schema this
 * small, and walking the array by hand is what makes an unreadable block cost its own content
 * rather than the whole note.
 */
object NoteJson {

    fun encode(document: NoteDocument): String {
        val array = JSONArray()
        document.blocks.forEach { block ->
            array.put(
                when (block) {
                    is Block.Text -> JSONObject()
                        .put("type", "text")
                        .put("id", block.id)
                        .put("kind", block.kind.name)
                        .put("text", block.text)
                        .put("checked", block.checked)
                        .put(
                            "spans",
                            JSONArray().apply {
                                block.spans.forEach { span ->
                                    put(
                                        JSONObject()
                                            .put("start", span.start)
                                            .put("end", span.end)
                                            .put("mark", span.mark.name),
                                    )
                                }
                            },
                        )

                    is Block.Image -> JSONObject()
                        .put("type", "image")
                        .put("id", block.id)
                        .put("sha256", block.sha256)
                        .put("widthPx", block.widthPx)
                        .put("heightPx", block.heightPx)
                },
            )
        }
        return array.toString()
    }

    /**
     * Never throws, and never answers with nothing. A block kind this version does not know
     * keeps whatever text it carried and loses the rest; content that is not JSON at all opens
     * as a paragraph holding the raw text. Principle 3: nothing is ever lost, including a note
     * the app has stopped understanding.
     */
    fun decode(raw: String, newId: () -> String): NoteDocument {
        val array = runCatching { JSONArray(raw) }.getOrNull()
            ?: return NoteDocument(listOf(Block.Text(id = newId(), text = raw)))

        val blocks = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { block(it, newId) }
        }
        return if (blocks.isEmpty()) NoteDocument.empty(newId) else NoteDocument(blocks)
    }

    private fun block(row: JSONObject, newId: () -> String): Block? {
        val id = row.optString("id").ifBlank { newId() }
        return when (row.optString("type")) {
            "text" -> Block.Text(
                id = id,
                kind = enumOrNull<TextKind>(row.optString("kind")) ?: TextKind.PARAGRAPH,
                text = row.optString("text"),
                spans = spans(row.optJSONArray("spans")),
                checked = row.optBoolean("checked"),
            )

            "image" -> Block.Image(
                id = id,
                sha256 = row.optString("sha256").ifBlank { return null },
                widthPx = row.optInt("widthPx"),
                heightPx = row.optInt("heightPx"),
            )

            // A kind from a later version. Keep its text if it had any, drop it if not --
            // an empty block the user cannot see is worse than one fewer block.
            else -> row.optString("text").takeIf { it.isNotBlank() }
                ?.let { Block.Text(id = id, text = it) }
        }
    }

    private fun spans(array: JSONArray?): List<Span> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val mark = enumOrNull<Mark>(row.optString("mark")) ?: return@mapNotNull null
            val start = row.optInt("start")
            val end = row.optInt("end")
            // A span that survived a bad write is not allowed to crash a note by pointing
            // outside its text; NoteEdits clamps on load rather than trusting the file.
            if (end > start) Span(start, end, mark) else null
        }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
