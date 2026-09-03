package com.wasif.khata.core.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only for org.json, which is a stub in a plain JVM test. */
@RunWith(RobolectricTestRunner::class)
class DriveWireTest {

    @Test
    fun `the payload survives the multipart body byte for byte`() {
        // A backup is AES-GCM output: uniformly random, mostly invalid UTF-8.
        // Building this body as a String would replace each invalid sequence with
        // U+FFFD and upload a file of roughly the right size and entirely the wrong
        // bytes -- undetectable until a restore fails. These four bytes are chosen
        // to break exactly that: a null, a CR, an LF, and a lone 0xFF.
        val payload = byteArrayOf(0x00, 0x0D, 0x0A, 0xFF.toByte())

        val body = multipartBody("""{"name":"x"}""", payload, "BOUND")

        val marker = "application/octet-stream\r\n\r\n".toByteArray()
        val start = body.indexOfSub(marker) + marker.size
        assertEquals(
            payload.toList(),
            body.copyOfRange(start, start + payload.size).toList(),
        )
    }

    @Test
    fun `the multipart body opens and closes on the boundary`() {
        val body = multipartBody("{}", byteArrayOf(1), "BOUND").toString(Charsets.ISO_8859_1)

        assertEquals(true, body.startsWith("--BOUND\r\n"))
        assertEquals(true, body.endsWith("\r\n--BOUND--"))
    }

    @Test
    fun `a file list becomes ids and names`() {
        val json = """{"files":[{"id":"a1","name":"khata-2.kbk"},{"id":"b2","name":"khata-1.kbk"}]}"""

        assertEquals(
            listOf(DriveFile("a1", "khata-2.kbk"), DriveFile("b2", "khata-1.kbk")),
            parseFileList(json),
        )
    }

    @Test
    fun `a malformed or empty list is empty, not a crash`() {
        // Drive answers with an error object rather than a file list when a token
        // has expired. A crash here would take down the worker.
        assertEquals(emptyList<DriveFile>(), parseFileList("""{"error":{"code":401}}"""))
        assertEquals(emptyList<DriveFile>(), parseFileList("not json at all"))
        assertEquals(emptyList<DriveFile>(), parseFileList("""{"files":[]}"""))
    }

    @Test
    fun `an entry missing an id is dropped rather than faked`() {
        val json = """{"files":[{"name":"khata-1.kbk"},{"id":"b2","name":"khata-2.kbk"}]}"""

        assertEquals(listOf(DriveFile("b2", "khata-2.kbk")), parseFileList(json))
    }

    @Test
    fun `a created file yields its id`() {
        assertEquals("folder-1", parseFileId("""{"id":"folder-1","name":"Khata"}"""))
        assertNull(parseFileId("""{"error":{"code":403}}"""))
        assertNull(parseFileId("garbage"))
    }

    @Test
    fun `pruning keeps seven and names the eighth`() {
        // Names sort by the millis in them, exactly as BackupRepository prunes
        // locally -- the two must agree or Drive drifts from the phone.
        val files = (1..9).map { DriveFile("id-$it", "khata-100$it.kbk") }

        val doomed = toDelete(files, keep = 7)

        assertEquals(listOf("khata-1001.kbk", "khata-1002.kbk"), doomed.map { it.name })
    }

    @Test
    fun `pruning below the limit deletes nothing`() {
        val files = (1..3).map { DriveFile("id-$it", "khata-100$it.kbk") }

        assertEquals(emptyList<DriveFile>(), toDelete(files, keep = 7))
    }

    private fun ByteArray.indexOfSub(needle: ByteArray): Int =
        (0..size - needle.size).first { i ->
            needle.indices.all { this[i + it] == needle[it] }
        }
}
