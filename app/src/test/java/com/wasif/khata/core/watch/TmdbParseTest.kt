package com.wasif.khata.core.watch

import com.wasif.khata.core.data.entity.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every literal here is copied verbatim from docs/superpowers/specs/tmdb-responses.md.
 * Those fixtures are written to TMDB's documented shape rather than captured from a
 * live call, so they are evidence about the parser and not about TMDB -- if a real
 * response ever disagrees, the real one wins and the fixture is corrected.
 */
// Robolectric for org.json, which is a platform class rather than a JVM one -- the
// same reason GeminiResponseTest needs it. Without it every parse returns empty,
// because the stubbed org.json throws and runCatching swallows it.
@RunWith(RobolectricTestRunner::class)
class TmdbParseTest {

    private val film =
        """{"page":1,"results":[{"id":949,"media_type":"movie","title":"Heat","original_title":"Heat","release_date":"1995-12-15","vote_average":7.9,"vote_count":7421,"poster_path":"/umSVjVdbVwtx5ryCA2QXL44Durm.jpg","adult":false}],"total_results":1}"""

    private val series =
        """{"page":1,"results":[{"id":1438,"media_type":"tv","name":"The Wire","original_name":"The Wire","first_air_date":"2002-06-02","vote_average":8.6,"vote_count":1502,"poster_path":"/4lbclFySvugI51fwsyxBTOm4DqK.jpg","origin_country":["US"]}],"total_results":1}"""

    private val person =
        """{"page":1,"results":[{"id":1158,"media_type":"person","name":"Al Pacino","known_for_department":"Acting","popularity":24.5,"profile_path":"/2dNqZmDLmVeUmnfWvzGjZoLZ2Xh.jpg"}],"total_results":1}"""

    private val unreleased =
        """{"page":1,"results":[{"id":123456,"media_type":"movie","title":"An Unfinished Film","release_date":"","vote_average":0.0,"vote_count":0,"poster_path":null}],"total_results":1}"""

    private val mixed =
        """{"page":1,"results":[{"id":949,"media_type":"movie","title":"Heat","release_date":"1995-12-15","vote_average":7.9,"poster_path":"/umSVjVdbVwtx5ryCA2QXL44Durm.jpg"},{"id":1158,"media_type":"person","name":"Al Pacino","profile_path":"/2dNqZmDLmVeUmnfWvzGjZoLZ2Xh.jpg"},{"id":1438,"media_type":"tv","name":"The Wire","first_air_date":"2002-06-02","vote_average":8.6,"poster_path":"/4lbclFySvugI51fwsyxBTOm4DqK.jpg"}],"total_results":3}"""

    private val empty = """{"page":1,"results":[],"total_pages":1,"total_results":0}"""

    private val truncated = """{"page":1,"resu"""

    @Test
    fun `F1 a film parses title, year, kind and rating`() {
        val heat = parseSearchResults(film).single()
        assertEquals(949, heat.tmdbId)
        assertEquals("Heat", heat.name)
        assertEquals(1995, heat.year)
        assertEquals(TitleKind.FILM, heat.kind)
        assertEquals(7.9, heat.rating!!, 0.001)
        assertEquals("/umSVjVdbVwtx5ryCA2QXL44Durm.jpg", heat.posterPath)
    }

    @Test
    fun `F2 a series reads name and first_air_date, not title and release_date`() {
        val wire = parseSearchResults(series).single()
        assertEquals(1438, wire.tmdbId)
        assertEquals("The Wire", wire.name)
        assertEquals(2002, wire.year)
        assertEquals(TitleKind.SERIES, wire.kind)
    }

    @Test
    fun `F3 a person is dropped`() {
        assertTrue(parseSearchResults(person).isEmpty())
    }

    @Test
    fun `F4 a missing poster and a blank date are absent, and an unrated title has no rating`() {
        val row = parseSearchResults(unreleased).single()
        assertNull(row.posterPath)
        assertNull(row.year)
        // 0.0 from TMDB means nobody rated it, which is not the same as a rating of zero.
        assertNull(row.rating)
    }

    @Test
    fun `F5 a mixed page keeps the titles in order and drops the person`() {
        assertEquals(listOf("Heat", "The Wire"), parseSearchResults(mixed).map { it.name })
    }

    @Test
    fun `F6 and F7 nothing found and unreadable both answer with an empty list`() {
        assertEquals(emptyList<TmdbResult>(), parseSearchResults(empty))
        assertEquals(emptyList<TmdbResult>(), parseSearchResults(truncated))
        assertEquals(emptyList<TmdbResult>(), parseSearchResults(""))
        assertEquals(emptyList<TmdbResult>(), parseSearchResults("<html>Gateway Timeout</html>"))
    }

    @Test
    fun `the poster url is the w500 size, joined without a double slash`() {
        assertEquals(
            "https://image.tmdb.org/t/p/w500/umSVjVdbVwtx5ryCA2QXL44Durm.jpg",
            posterUrl("/umSVjVdbVwtx5ryCA2QXL44Durm.jpg"),
        )
    }
}
