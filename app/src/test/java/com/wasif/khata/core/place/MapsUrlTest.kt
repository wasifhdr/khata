package com.wasif.khata.core.place

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CORPUS = "docs/superpowers/specs/maps-urls.md"

/**
 * Reads the corpus rather than restating it, so adding a row to the markdown is
 * adding a test. Every literal lives in one place and the spacing is never tidied.
 */
class MapsUrlTest {

    private data class Row(val input: String, val name: String?, val lat: Double?, val lng: Double?)

    private fun corpus(): List<Row> {
        // Run from the repo root under Gradle and from app/ under some IDE runners.
        val candidates = listOf(File(CORPUS), File("../$CORPUS"))
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("cannot locate $CORPUS; looked in ${candidates.map { it.absolutePath }}", file)

        return file!!.readLines()
            .filter { it.trimStart().startsWith("| `") }
            .map { line ->
                val cells = line.trim().trim('|').split('|').map { it.trim() }
                Row(
                    input = cells[0].trim('`').replace("⏎", "\n"),
                    name = cells[1].ifBlank { null },
                    lat = cells[2].ifBlank { null }?.toDouble(),
                    lng = cells[3].ifBlank { null }?.toDouble(),
                )
            }
    }

    @Test
    fun `every corpus row parses as the corpus says`() {
        val rows = corpus()
        // A corpus that silently stopped being found would pass every assertion below.
        assertEquals(7, rows.size)

        rows.forEach { row ->
            val parsed = parseSharedPlace(row.input)
            assertNotNull("no parse at all for: ${row.input}", parsed)
            assertEquals("name for: ${row.input}", row.name, parsed!!.name)
            assertEquals("lat for: ${row.input}", row.lat, parsed.lat)
            assertEquals("lng for: ${row.input}", row.lng, parsed.lng)
        }
    }

    @Test
    fun `the place wins over the camera`() {
        // @lat,lng is where the map happened to be centred; !3d!4d is the place. They
        // disagree whenever the user panned before sharing, and the camera is wrong.
        val parsed = parseSharedPlace(
            "https://www.google.com/maps/place/X/@23.80,90.40,17z/data=!3d23.7461!4d90.3742",
        )!!
        assertEquals(23.7461, parsed.lat!!, 0.0001)
    }

    @Test
    fun `text with neither a link nor coordinates is not a place`() {
        assertEquals(null, parseSharedPlace("let's get dinner"))
        assertEquals(null, parseSharedPlace(""))
    }

    @Test
    fun `a short link keeps its url even with nothing else to show for it`() {
        // Offline is a normal outcome for a link, not an error: the place stays
        // openable in Maps whether or not it ever resolved.
        val parsed = parseSharedPlace("https://maps.app.goo.gl/AbCdEf")!!
        assertTrue(parsed.url == "https://maps.app.goo.gl/AbCdEf")
    }
}
