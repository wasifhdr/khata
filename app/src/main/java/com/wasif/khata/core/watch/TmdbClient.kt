package com.wasif.khata.core.watch

import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.prefs.PreferencesRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val SEARCH_ENDPOINT = "https://api.themoviedb.org/3/search/multi"
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

/** One TMDB row, reduced to the six fields this module stores and nothing else. */
data class TmdbResult(
    val tmdbId: Int,
    val name: String,
    val year: Int?,
    val kind: TitleKind,
    /** TMDB's own 0-10 scale, kept as given. Never converted into the user's stars. */
    val rating: Double?,
    val posterPath: String?,
)

fun posterUrl(posterPath: String): String = IMAGE_BASE + posterPath

/**
 * Empty on anything that is not a usable list: malformed JSON, no results array, or
 * rows of a kind this module does not hold. The manual fields sit beneath the search
 * box on screen, so there is nothing to recover from and nothing to explain.
 */
fun parseSearchResults(json: String): List<TmdbResult> = runCatching {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()

    (0 until results.length()).mapNotNull { i ->
        val row = results.optJSONObject(i) ?: return@mapNotNull null

        // A multi search returns people as well. Nothing here holds a person.
        val kind = when (row.optString("media_type")) {
            "movie" -> TitleKind.FILM
            "tv" -> TitleKind.SERIES
            else -> return@mapNotNull null
        }

        // A film carries title/release_date; a series carries name/first_air_date.
        // Reading the wrong pair yields a blank title rather than an error, which is
        // why the fixture corpus holds one of each.
        val name = (if (kind == TitleKind.FILM) row.optString("title") else row.optString("name"))
            .ifBlank { return@mapNotNull null }
        val date = if (kind == TitleKind.FILM) {
            row.optString("release_date")
        } else {
            row.optString("first_air_date")
        }

        TmdbResult(
            tmdbId = row.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null,
            name = name,
            // An unreleased title has an empty date, which is absent rather than 0 AD.
            year = date.take(4).toIntOrNull(),
            kind = kind,
            // A title nobody has rated comes back as 0.0, which is not a rating.
            rating = row.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            posterPath = row.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}.getOrDefault(emptyList())

@Singleton
class TmdbClient @Inject constructor(
    private val preferences: PreferencesRepository,
) {
    /**
     * Empty on no key, no network, a non-200, or unreadable JSON -- every failure is
     * the same failure, because the answer to all of them is the same: type it in.
     */
    suspend fun search(query: String): List<TmdbResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val key = preferences.preferences.first().tmdbKey ?: return@withContext emptyList()

        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = (URL("$SEARCH_ENDPOINT?api_key=$key&query=$encoded").openConnection() as HttpURLConnection)
            .apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                // Worth a line, for the same reason GeminiClient logs its own: a bad
                // key, a rate limit and a missing INTERNET permission are otherwise
                // indistinguishable from "TMDB has never heard of this film".
                android.util.Log.w("KhataTmdb", "search -> $code")
                return@withContext emptyList()
            }
            parseSearchResults(connection.inputStream.bufferedReader().readText())
        } catch (e: IOException) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The rating alone, for the refresh on opening a title. Searching by name and
     * matching the id back is one endpoint rather than two: this module already has
     * search, and a second path would be a second thing to keep working.
     */
    suspend fun rating(tmdbId: Int, name: String): Double? =
        search(name).firstOrNull { it.tmdbId == tmdbId }?.rating

    /** Null on any failure, so a poster that will not download is simply absent. */
    suspend fun poster(posterPath: String): ByteArray? = withContext(Dispatchers.IO) {
        val connection = (URL(posterUrl(posterPath)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            connection.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
